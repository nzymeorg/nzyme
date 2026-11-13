use anyhow::{bail, Error};

const ADDRESS_PREFIX: &str = "bt-";

#[derive(Debug, Clone, Eq, PartialEq)]
pub struct Adapter {
    pub name: String,
    pub address: String
}

#[derive(Debug, Clone, Eq, PartialEq)]
pub enum AdapterSelector {
    Name(String),
    Address(String)
}

impl AdapterSelector {

    pub fn from_interface_name(interface_name: &str) -> Result<Self, Error> {
        match interface_name.strip_prefix(ADDRESS_PREFIX) {
            Some(address) => {
                if !is_valid_address(address) {
                    bail!("Invalid Bluetooth interface name [{}]. Expected an adapter address \
                        like [{}08:BE:AC:4D:34:F2].", interface_name, ADDRESS_PREFIX);
                }

                Ok(AdapterSelector::Address(address.to_uppercase()))
            },
            None => Ok(AdapterSelector::Name(interface_name.to_string()))
        }
    }

    pub fn resolve(&self, adapters: &[Adapter]) -> Result<String, Error> {
        match self {
            AdapterSelector::Name(name) => {
                if adapters.iter().any(|a| &a.name == name) {
                    Ok(name.clone())
                } else {
                    bail!("Bluetooth adapter [{}] not found. Present adapters: {}. Adapter \
                        names can change when an adapter resets. Configure the interface by \
                        address to follow the adapter.", name, describe(adapters));
                }
            },
            AdapterSelector::Address(address) => {
                let matches: Vec<&Adapter> = adapters.iter()
                    .filter(|a| a.address.eq_ignore_ascii_case(address))
                    .collect();

                match matches.as_slice() {
                    [adapter] => Ok(adapter.name.clone()),
                    [] => bail!("No Bluetooth adapter with address [{}] found. Present \
                        adapters: {}.", address, describe(adapters)),
                    _ => bail!("Multiple Bluetooth adapters with address [{}] found. Present \
                        adapters: {}. Configure the interface by name instead.",
                        address, describe(adapters))
                }
            }
        }
    }

}

fn is_valid_address(address: &str) -> bool {
    let octets: Vec<&str> = address.split(':').collect();

    octets.len() == 6
        && octets.iter().all(|o| o.len() == 2 && o.chars().all(|c| c.is_ascii_hexdigit()))
}

fn describe(adapters: &[Adapter]) -> String {
    if adapters.is_empty() {
        return "none".to_string();
    }

    let mut described: Vec<String> = adapters.iter()
        .map(|a| format!("{} ({})", a.name, a.address))
        .collect();
    described.sort();

    described.join(", ")
}

#[cfg(test)]
mod tests {
    use super::*;

    fn adapter(name: &str, address: &str) -> Adapter {
        Adapter { name: name.to_string(), address: address.to_string() }
    }

    #[test]
    fn parses_names_and_addresses() {
        assert_eq!(
            AdapterSelector::from_interface_name("hci0").unwrap(),
            AdapterSelector::Name("hci0".to_string())
        );
        assert_eq!(
            AdapterSelector::from_interface_name("bt-08:be:ac:4d:34:f2").unwrap(),
            AdapterSelector::Address("08:BE:AC:4D:34:F2".to_string())
        );
    }

    #[test]
    fn rejects_invalid_addresses() {
        for name in ["bt-", "bt-hci0", "bt-08:BE:AC:4D:34", "bt-08:BE:AC:4D:34:F2:00",
            "bt-08:BE:AC:4D:34:ZZ", "bt-08BEAC4D34F2", "bt-08:BE:AC:4D:34:F"] {
            assert!(AdapterSelector::from_interface_name(name).is_err(), "{}", name);
        }
    }

    #[test]
    fn address_follows_adapter_across_names() {
        let selector = AdapterSelector::from_interface_name("bt-08:BE:AC:4D:34:F2").unwrap();

        let before = [adapter("hci0", "F4:C8:8A:2E:DF:78"), adapter("hci1", "08:BE:AC:4D:34:F2")];
        let after = [adapter("hci0", "F4:C8:8A:2E:DF:78"), adapter("hci2", "08:BE:AC:4D:34:F2")];

        assert_eq!(selector.resolve(&before).unwrap(), "hci1");
        assert_eq!(selector.resolve(&after).unwrap(), "hci2");
    }

    #[test]
    fn address_fails_when_missing_or_ambiguous() {
        let selector = AdapterSelector::from_interface_name("bt-08:BE:AC:4D:34:F2").unwrap();

        assert!(selector.resolve(&[]).is_err());
        assert!(selector.resolve(&[adapter("hci0", "F4:C8:8A:2E:DF:78")]).is_err());
        assert!(selector.resolve(&[
            adapter("hci0", "08:BE:AC:4D:34:F2"), adapter("hci1", "08:BE:AC:4D:34:F2")
        ]).is_err());
    }

    #[test]
    fn name_resolves_to_itself_only_when_present() {
        let selector = AdapterSelector::from_interface_name("hci1").unwrap();

        assert_eq!(selector.resolve(&[adapter("hci1", "08:BE:AC:4D:34:F2")]).unwrap(), "hci1");
        assert!(selector.resolve(&[adapter("hci2", "08:BE:AC:4D:34:F2")]).is_err());
    }
}
