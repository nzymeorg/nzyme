use std::collections::HashMap;
use std::panic::catch_unwind;
use std::sync::{Arc, Mutex};
use std::thread::sleep;
use std::time::Duration;
use anyhow::{bail, Context, Error};
use chrono::Utc;
use dbus::arg;
use dbus::arg::{RefArg, Variant};
use dbus::blocking::{Connection};
use dbus::blocking::stdintf::org_freedesktop_dbus::Properties;
use log::{debug, error, info, warn};
use crate::wireless::bluetooth::adapter::{Adapter, AdapterSelector};
use crate::wireless::bluetooth::bluetooth_device_advertisement::BluetoothDeviceAdvertisement;
use crate::configuration::BluetoothInterface;
use crate::messagebus::bus::Bus;
use crate::messagebus::channel_names::BluetoothChannelName;
use crate::metrics::Metrics;
use crate::to_pipeline;

pub struct Capture {
    pub metrics: Arc<Mutex<Metrics>>,
    pub bus: Arc<Bus>,
    pub configuration: BluetoothInterface
}

const DBUS_INTERFACE: &str = "org.bluez.Adapter1";
const ADAPTER_PATH_PREFIX: &str = "/org/bluez/";

type ManagedObjects = HashMap<dbus::Path<'static>, HashMap<String, HashMap<String, Variant<Box<dyn RefArg>>>>>;

impl Capture {

    pub fn run(&mut self, interface_name: &str) {
        info!("Starting Bluetooth capture on [{}]", interface_name);

        let selector = match AdapterSelector::from_interface_name(interface_name) {
            Ok(selector) => selector,
            Err(e) => {
                error!("Could not start Bluetooth capture: {}", e);
                return;
            }
        };

        let mut transports = Vec::new();
        if self.configuration.bt_classic_enabled {
            transports.push(("bredr", "Bluetooth Classic"));
        }
        if self.configuration.bt_le_enabled {
            transports.push(("le", "Bluetooth LE"));
        }

        let mut adapter_name: Option<String> = None;

        loop {
            let result = catch_unwind(|| {
                let mut discovered_on = None;

                for (transport, transport_name) in &transports {
                    match self.discover_devices(interface_name, &selector, transport) {
                        Ok((adapter, devices)) => {
                            self.discovered_devices_to_pipeline(interface_name, devices);
                            discovered_on = Some(adapter);
                        },
                        Err(e) => {
                            error!("Could not discover {} devices on [{}]: {:#}",
                                transport_name, interface_name, e);
                        }
                    }
                }

                discovered_on
            });

            let discovered_on = match result {
                Ok(discovered_on) => discovered_on,
                Err(e) => {
                    match e.downcast_ref::<&str>() { Some(s) => {
                        error!("Could not discover Bluetooth devices: {}", s);
                    } _ => { match e.downcast_ref::<String>() { Some(s) => {
                        error!("Could not discover Bluetooth devices: {}", s);
                    } _ => {
                        error!("Could not discover Bluetooth devices. Panicked with an unknown type.");
                    }}}}

                    None
                }
            };

            if let Some(adapter) = &discovered_on {
                if adapter != interface_name && adapter_name.as_ref() != Some(adapter) {
                    match &adapter_name {
                        Some(previous) => info!("Bluetooth interface [{}] moved from adapter \
                            [{}] to [{}].", interface_name, previous, adapter),
                        None => info!("Bluetooth interface [{}] is adapter [{}].",
                            interface_name, adapter)
                    }
                }

                adapter_name = discovered_on.clone();
            }

            match self.metrics.lock() {
                Ok(mut metrics) => {
                    if discovered_on.is_some() {
                        metrics.update_capture(interface_name, true, 0, 0, false);
                    } else {
                        metrics.mark_capture_as_failed(interface_name);
                    }
                },
                Err(e) => error!("Could not acquire metrics mutex: {}", e)
            }

            /*
             * The discovery methods sleep during discovery, but we add another sleep to make sure
             * we don't empty spin in case of errors early in the discovery methods.
             */
            sleep(Duration::from_secs(1))
        }
    }

    pub fn discover_devices(&self,
                            interface_name: &str,
                            selector: &AdapterSelector,
                            transport: &str)
        -> Result<(String, HashMap<String, BluetoothDeviceAdvertisement>), Error> {

        /*
         * Using a Map to avoid unlikely case of multiple reports per discovery cycle from
         * same device.
         */
        let mut discovered: HashMap<String, BluetoothDeviceAdvertisement> = HashMap::new();

        let timeout = Duration::from_secs(self.configuration.dbus_method_call_timeout_seconds as u64);

        // Connect do D-Bus.
        let conn = Connection::new_system().context("Could not establish connection to D-Bus")?;

        let adapter_name = selector.resolve(&Self::adapters(&Self::managed_objects(&conn, timeout)?))?;

        // Obtain bluez reference.
        let adapter = conn.with_proxy(
            "org.bluez",
            format!("{}{}", ADAPTER_PATH_PREFIX, adapter_name),
            timeout
        );

        // Set the adapter to not discoverable and not pairable.
        adapter.set(DBUS_INTERFACE, "Discoverable", false)
            .context("Could not set Discoverable=false on device")?;
        adapter.set(DBUS_INTERFACE, "Pairable", false)
            .context("Could not set Pairable=false on device")?;

        // Set the discovery filter to set transport to selected method.
        let mut filter = arg::PropMap::new();
        filter.insert("Transport".to_string(), Variant(Box::new(transport.to_string())));
        adapter.method_call::<(), _, _, _>(DBUS_INTERFACE, "SetDiscoveryFilter", (filter,))
            .context("Could not set discovery filter")?;

        // Start bluetooth discovery.
        adapter.method_call::<(), _, _, _>(DBUS_INTERFACE, "StartDiscovery", ())
            .context("Could not start discovery")?;

        // Sleep to allow discovery.
        sleep(Duration::from_secs(self.configuration.discovery_period_seconds as u64));

        // Access the object manager to list all devices
        let devices = Self::managed_objects(&conn, timeout)?;

        /*
         * If the adapter reset during discovery, the name can now belong to another adapter and
         * the devices below it would not be ours.
         */
        let current_adapter_name = selector.resolve(&Self::adapters(&devices))?;
        if current_adapter_name != adapter_name {
            bail!("Adapter moved from [{}] to [{}] during discovery", adapter_name, current_adapter_name);
        }

        // Iterate over all discovered devices.
        for (path, interfaces) in devices {
            if path.to_string().starts_with(&format!("{}{}/dev_", ADAPTER_PATH_PREFIX, adapter_name)) {
                // Only discovered bluetooth devices.
                if let Some(props) = interfaces.get("org.bluez.Device1") {
                    /*
                     * Is this device connected to the Bluetooth adapter we are listening on?
                     * Notify in log because this will negatively impact discovery. This is most
                     * like a bluetooth device connected to the machine this tap runs on. The user
                     * should either disconnect (and forget) the device or use another bluetooth
                     * adapter that has no devices connected.
                     */
                    if let Some(true) = Self::parse_optional_bool_prop(props, "Connected") {
                        warn!("Bluetooth adapter [{}] has a connected device. This will \
                        negatively impact discovery. Disconnected and un-pair all bluetooth \
                        devices from this machine. Device: {:?}", interface_name, props)
                    }

                    // Mandatory fields.
                    let mac = Self::parse_mandatory_string_prop(props, "Address");
                    let alias = Self::parse_mandatory_string_prop(props, "Alias");

                    // Optional fields.
                    let rssi = Self::parse_optional_i16_prop(props, "RSSI");
                    let tx_power = Self::parse_optional_i16_prop(props, "TxPower");
                    let name = Self::parse_optional_string_prop(props, "Name");
                    let class = Self::parse_optional_u32_prop(props, "Class");
                    let appearance = Self::parse_optional_u32_prop(props, "Appearance");
                    let modalias = Self::parse_optional_string_prop(props, "Modalias");
                    let uuids = Self::parse_optional_string_vector(props, "UUIDs");
                    let service_data = Self::parse_optional_string_vector(props, "ServiceData");

                    // Manufacturer data incl. company ID.
                    let (company_id, manufacturer_data) = if let Some(v) = props.get("ManufacturerData") {
                        Self::parse_manufacturer_data(v)
                    } else {
                        (None, None)
                    };
                    
                    discovered.insert(mac.clone(), BluetoothDeviceAdvertisement {
                        mac,
                        name,
                        rssi,
                        company_id,
                        alias,
                        class,
                        appearance,
                        modalias,
                        tx_power,
                        manufacturer_data,
                        uuids,
                        service_data,
                        device: interface_name.to_string(),
                        transport: transport.to_string(),
                        timestamp: Utc::now(),
                    });
                }
            }
        }

        // Stop discovery
        adapter.method_call::<(), _, _, _>(DBUS_INTERFACE, "StopDiscovery", ())
            .context("Could not stop discovery")?;

        Ok((adapter_name, discovered))
    }

    fn managed_objects(conn: &Connection, timeout: Duration) -> Result<ManagedObjects, Error> {
        let obj_manager = conn.with_proxy("org.bluez", "/", timeout);

        let (objects, ): (ManagedObjects, ) =
            obj_manager.method_call("org.freedesktop.DBus.ObjectManager", "GetManagedObjects", ())
                .context("Could not fetch objects from object manager")?;

        Ok(objects)
    }

    fn adapters(objects: &ManagedObjects) -> Vec<Adapter> {
        objects.iter()
            .filter_map(|(path, interfaces)| {
                let props = interfaces.get(DBUS_INTERFACE)?;

                Some(Adapter {
                    name: path.strip_prefix(ADAPTER_PATH_PREFIX)?.to_string(),
                    address: props.get("Address")?.as_str()?.to_string()
                })
            })
            .collect()
    }

    fn discovered_devices_to_pipeline(&self,
                                      interface_name: &str,
                                      devices: HashMap<String, BluetoothDeviceAdvertisement>) {
        for device in devices.values() {
            to_pipeline!(
                BluetoothChannelName::BluetoothDevicesPipeline,
                self.bus.bluetooth_device_pipeline.sender,
                Arc::new(device.clone()),
                1024
            );

            match self.metrics.lock() {
                Ok(mut metrics) => {
                    metrics.increment_processed_bytes_total(device.estimate_struct_size());
                    metrics.update_capture(interface_name, true, 0, 0, true);
                },
                Err(e) => error!("Could not acquire metrics mutex: {}", e)
            }
        }
    }

    fn parse_mandatory_string_prop(props: &HashMap<String, Variant<Box<dyn RefArg>>>, name: &str)
        -> String {

        Self::parse_optional_string_prop(props, name).unwrap_or_else(|| {
            warn!("Invalid Bluetooth advertisement, not containing [{}]: {:?}", name, props);
            "Invalid".to_string()
        })
    }

    fn parse_optional_string_prop(props: &HashMap<String, Variant<Box<dyn RefArg>>>, name: &str)
                                  -> Option<String> {
        props.get(name).and_then(|v|
        v.as_str()
            .map(|val| val.to_string())
            .or_else(|| {
                warn!("Invalid Bluetooth advertisement, [{}] not a string: {:?}", name, props);
                None
            })
        )
    }

    fn parse_optional_i16_prop(props: &HashMap<String, Variant<Box<dyn RefArg>>>, name: &str)
        -> Option<i16> {
        props.get(name).and_then(|v| {
            match v.as_i64() {
                Some(val) if val <= i16::MAX as i64 => Some(val as i16),
                Some(_) => {
                    warn!("Invalid Bluetooth advertisement, [{}] value out of range for i64: {:?}", name, props);
                    None
                },
                None => {
                    warn!("Invalid Bluetooth advertisement, [{}] not u64 or not present: {:?}", name, props);
                    None
                }
            }
        })
    }

    fn parse_optional_u32_prop(props: &HashMap<String, Variant<Box<dyn RefArg>>>, name: &str)
        -> Option<u32> {
        props.get(name).and_then(|v| {
            match v.as_u64() {
                Some(val) if val <= u32::MAX as u64 => Some(val as u32),
                Some(_) => {
                    warn!("Invalid Bluetooth advertisement, [{}] value out of range for u32: {:?}", name, props);
                    None
                },
                None => {
                    warn!("Invalid Bluetooth advertisement, [{}] not u64 or not present: {:?}", name, props);
                    None
                }
            }
        })
    }

    fn parse_optional_bool_prop(props: &HashMap<String, Variant<Box<dyn RefArg>>>, name: &str)
        -> Option<bool> {

        props.get(name).and_then(|v| {
            v.0.as_any().downcast_ref::<bool>().copied().or_else(|| {
                warn!("Invalid Bluetooth advertisement, [{}] not bool: {:?}", name, props);
                None
            })
        })
    }

    fn parse_optional_string_vector(props: &HashMap<String, Variant<Box<dyn RefArg>>>, name: &str)
        -> Option<Vec<String>> {

        if let Some(v) = props.get(name) {
            match v.0.as_iter() {
                Some(iter) => {
                    let mut data = Vec::new();
                    for val in iter {
                        match val.as_str() {
                            Some(str) => {
                                data.push(str.to_string())
                            },
                            None => {
                                // Ignore non-string elements.
                                debug!("Invalid Bluetooth advertisement, [{}] includes \
                                element that is not a string: {:?}", name, props);
                            }
                        }
                    }

                    if data.is_empty() {
                        None
                    } else {
                        Some(data)
                    }
                },
                None => None
            }
        } else {
            None
        }
    }

    fn parse_manufacturer_data(manufacturer_data_var: &Variant<Box<dyn RefArg>>)
        -> (Option<u16>, Option<Vec<u8>>) {

        let company_id = match manufacturer_data_var.0.as_iter() { Some(mut iter) => {
            match iter.nth(0) {
                Some(key) => key.as_u64().map(|val| val as u16),
                None => None
            }
        } _ => {
            None
        }};

        let data = manufacturer_data_var.0.as_iter().and_then(|mut iter| {
            iter.nth(1)?.as_iter().and_then(|mut iter| {
                iter.nth(0)?.as_iter().map(|iter| {
                    iter.filter_map(|val| val.as_u64().map(|v| v as u8)).collect::<Vec<u8>>()
                })
            })
        });

        (company_id, data)
    }

}