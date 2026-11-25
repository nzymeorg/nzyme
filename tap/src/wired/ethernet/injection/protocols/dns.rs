use std::net::IpAddr;

pub fn ptr_name(ip: IpAddr) -> String {
    match ip {
        IpAddr::V4(ip) => {
            let octets = ip.octets();
            format!("{}.{}.{}.{}.in-addr.arpa", octets[3], octets[2], octets[1], octets[0])
        },
        IpAddr::V6(ip) => {
            // Every hex digit of the address is a label, last digit first.
            let mut name = String::with_capacity(72);
            for byte in ip.octets().iter().rev() {
                name.push_str(&format!("{:x}.{:x}.", byte & 0x0f, byte >> 4));
            }
            name.push_str("ip6.arpa");
            name
        }
    }
}

pub fn ptr_query(ip: IpAddr, transaction_id: u16) -> Vec<u8> {
    let mut query: Vec<u8> = Vec::new();

    // DNS Header.
    query.extend_from_slice(&transaction_id.to_be_bytes()); // Transaction ID.

    let flags: u16 = 0x0100; // Standard query.
    query.extend_from_slice(&flags.to_be_bytes()); // Flags

    let qdcount: u16 = 1; // One question
    query.extend_from_slice(&qdcount.to_be_bytes()); // Number of questions.

    let ancount: u16 = 0;
    query.extend_from_slice(&ancount.to_be_bytes()); // Number of answers.

    let nscount: u16 = 0;
    query.extend_from_slice(&nscount.to_be_bytes()); // Number of authority records.

    let arcount: u16 = 0;
    query.extend_from_slice(&arcount.to_be_bytes()); // Number of additional records.

    // Question Section.
    for label in ptr_name(ip).split('.') {
        query.push(label.len() as u8); // Length of the label.
        query.extend_from_slice(label.as_bytes()); // Label.
    }

    query.push(0); // End of the QNAME.

    let qtype: u16 = 12; // PTR query
    query.extend_from_slice(&qtype.to_be_bytes()); // QTYPE.

    let qclass: u16 = 1; // IN class
    query.extend_from_slice(&qclass.to_be_bytes()); // QCLASS.

    query
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn ptr_names() {
        assert_eq!(ptr_name("192.168.1.20".parse().unwrap()), "20.1.168.192.in-addr.arpa");
        assert_eq!(ptr_name("2001:db8:1200:34::5".parse().unwrap()),
                   "5.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.4.3.0.0.0.0.2.1.8.b.d.0.1.0.0.2.ip6.arpa");
    }

    #[test]
    fn ptr_query_for_an_ipv6_address() {
        let query = ptr_query("2001:db8::1".parse().unwrap(), 0xbeef);

        assert_eq!(&query[0..2], &[0xbe, 0xef]);
        // 12 bytes of header, 32 labels of one digit, "ip6", "arpa", the root label, type and class.
        assert_eq!(query.len(), 12 + 32 * 2 + 4 + 5 + 1 + 4);
        assert_eq!(&query[12..14], &[1, b'1']);
        assert_eq!(&query[query.len() - 4..], &[0, 12, 0, 1]);
    }
}
