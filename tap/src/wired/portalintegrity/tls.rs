use std::sync::{Arc, Mutex};

use rustls::client::danger::{HandshakeSignatureValid, ServerCertVerified, ServerCertVerifier};
use rustls::pki_types::{CertificateDer, ServerName, UnixTime};
use rustls::{ClientConfig, DigitallySignedStruct, SignatureScheme};

use sha2::{Digest, Sha256};

#[derive(Debug, Clone, Default)]
pub struct CapturedTls {
    pub chain_der: Vec<Vec<u8>>,
    pub leaf_sha256: String,
    pub leaf_subject: String,
    pub leaf_issuer: String,
    pub leaf_not_before: String,
    pub leaf_not_after: String,
    pub leaf_sans: Vec<String>,
    pub protocol_version: Option<String>,
    pub cipher_suite: Option<u16>,
    pub sni: Option<String>,
}

pub type TlsSlot = Arc<Mutex<Option<CapturedTls>>>;

#[derive(Debug)]
struct CaptureVerifier {
    slot: TlsSlot,
}

impl CaptureVerifier {
    fn capture(&self, end_entity: &CertificateDer<'_>, intermediates: &[CertificateDer<'_>]) {
        let mut rec = CapturedTls::default();

        // Full chain, leaf first, as raw DER.
        rec.chain_der.push(end_entity.as_ref().to_vec());
        for ic in intermediates {
            rec.chain_der.push(ic.as_ref().to_vec());
        }

        // Leaf fingerprint over the raw DER.
        let mut hasher = Sha256::new();
        hasher.update(end_entity.as_ref());
        rec.leaf_sha256 = hex(&hasher.finalize());

        // Best-effort parse of the leaf for convenience fields.
        if let Ok((_, cert)) = x509_parser::parse_x509_certificate(end_entity.as_ref()) {
            rec.leaf_subject = cert.subject().to_string();
            rec.leaf_issuer = cert.issuer().to_string();
            rec.leaf_not_before = cert.validity().not_before.to_string();
            rec.leaf_not_after = cert.validity().not_after.to_string();
            if let Ok(Some(san)) = cert.subject_alternative_name() {
                for name in &san.value.general_names {
                    rec.leaf_sans.push(format!("{:?}", name));
                }
            }
        }

        if let Ok(mut guard) = self.slot.lock() {
            *guard = Some(rec);
        }
    }
}

impl ServerCertVerifier for CaptureVerifier {
    fn verify_server_cert(
        &self,
        end_entity: &CertificateDer<'_>,
        intermediates: &[CertificateDer<'_>],
        _server_name: &ServerName<'_>,
        _ocsp_response: &[u8],
        _now: UnixTime,
    ) -> Result<ServerCertVerified, rustls::Error> {
        self.capture(end_entity, intermediates);
        Ok(ServerCertVerified::assertion())
    }

    fn verify_tls12_signature(
        &self,
        _message: &[u8],
        _cert: &CertificateDer<'_>,
        _dss: &DigitallySignedStruct,
    ) -> Result<HandshakeSignatureValid, rustls::Error> {
        Ok(HandshakeSignatureValid::assertion())
    }

    fn verify_tls13_signature(
        &self,
        _message: &[u8],
        _cert: &CertificateDer<'_>,
        _dss: &DigitallySignedStruct,
    ) -> Result<HandshakeSignatureValid, rustls::Error> {
        Ok(HandshakeSignatureValid::assertion())
    }

    fn supported_verify_schemes(&self) -> Vec<SignatureScheme> {
        vec![
            SignatureScheme::RSA_PKCS1_SHA256,
            SignatureScheme::RSA_PKCS1_SHA384,
            SignatureScheme::RSA_PKCS1_SHA512,
            SignatureScheme::ECDSA_NISTP256_SHA256,
            SignatureScheme::ECDSA_NISTP384_SHA384,
            SignatureScheme::RSA_PSS_SHA256,
            SignatureScheme::RSA_PSS_SHA384,
            SignatureScheme::RSA_PSS_SHA512,
            SignatureScheme::ED25519,
        ]
    }
}

pub fn install_crypto_provider() {
    let _ = rustls::crypto::ring::default_provider().install_default();
}

pub fn client_config(slot: TlsSlot) -> Arc<ClientConfig> {
    let verifier = Arc::new(CaptureVerifier { slot });
    let config = ClientConfig::builder()
        .dangerous()
        .with_custom_certificate_verifier(verifier)
        .with_no_client_auth();
    Arc::new(config)
}

fn hex(bytes: &[u8]) -> String {
    let mut s = String::with_capacity(bytes.len() * 2);
    for b in bytes {
        s.push_str(&format!("{:02x}", b));
    }
    s
}