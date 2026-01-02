# Monero High-Level Wallet Implementation

> Pure Kotlin Multiplatform (KMP) and Dart implementations of Monero wallet functionality.

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

## 🎯 Project Goals

Provide native, high-performance Monero wallet libraries for mobile platforms without relying on C++ bindings:

- **KMP (Kotlin Multiplatform)**: Android, iOS, Desktop (JVM)
- **Dart**: Flutter applications

Both implementations are **independent** - no shared native code, each uses its platform's strengths.

## 📁 Project Structure

```
monero_high_level/
├── kmp/                          # Kotlin Multiplatform implementation
│   ├── monero-crypto/            # Cryptographic primitives
│   ├── monero-core/              # Address, mnemonic, keys
│   ├── monero-net/               # Daemon RPC client (Ktor)
│   ├── monero-storage/           # Encrypted storage (SQLDelight)
│   └── monero-wallet/            # High-level wallet API
│
├── dart/                         # Pure Dart implementation
│   └── lib/src/
│       ├── crypto/               # Keccak, Ed25519, Base58
│       ├── core/                 # Address, mnemonic, keys
│       ├── network/              # Daemon RPC client (Dio)
│       ├── storage/              # Wallet storage interface
│       ├── transaction/          # Tx builder, decoy selection
│       └── wallet/               # High-level wallet API
│
└── docs/                         # Design documents
```

## 🔧 Technology Stack

| Component | KMP | Dart |
|-----------|-----|------|
| Language | Kotlin 2.3.0 | Dart 3.10.7 |
| HTTP Client | Ktor 3.3.3 | Dio 5.7.0 |
| Storage | SQLDelight 2.0.2 | - |
| Crypto | Pure Kotlin | pointycastle |
| Testing | Kotest 5.9.1 | dart test |

## 🚀 Quick Start

## 📚 Documentation

- [docs/e2e.md](docs/e2e.md): how to run E2E (offline + regtest)
- [docs/dart.md](docs/dart.md): Dart package usage and examples
- [docs/kmp.md](docs/kmp.md): KMP module overview (entry point)
- [kmp/README.md](kmp/README.md): KMP usage and examples
- [docs/architecture.md](docs/architecture.md): repo/module overview
- [docs/contributing.md](docs/contributing.md): development workflow
- [docs/faq.md](docs/faq.md): common issues
- [docs/design.md](docs/design.md): architecture design
- [docs/requirements.md](docs/requirements.md): scope + requirements
- [docs/task.md](docs/task.md): task checklist
- [docs/init.md](docs/init.md): feasibility/implementation notes

### KMP

```bash
cd kmp
./gradlew build

# Run tests
./gradlew :monero-crypto:jvmTest
```

### Dart

```bash
cd dart
dart pub get

# Run tests
dart test

# Optional: E2E against a real monero-wallet-rpc (offline)
brew install monero
MONERO_E2E=1 dart test test/e2e/wallet_rpc_e2e_test.dart

# Optional: Full regtest E2E (starts monerod + monero-wallet-rpc)
MONERO_REGTEST_E2E=1 dart test test/e2e/wallet_rpc_regtest_e2e_test.dart
```

## 🧪 E2E notes (Dart)

- Offline E2E starts `monero-wallet-rpc` with `--offline --no-initial-sync` and validates common RPC flows.
- Regtest E2E starts a local `monerod --regtest` (offline, fixed difficulty) and `monero-wallet-rpc`, mines blocks via daemon RPC `generateblocks`, performs a transfer, then mines confirmation blocks.
- See [docs/e2e.md](docs/e2e.md) for details and troubleshooting.

## 📦 Modules

### Cryptographic Primitives

| Algorithm | Purpose | Status |
|-----------|---------|--------|
| Keccak-256 | Hashing | ✅ Complete |
| Ed25519 | Curve operations | 🔄 In Progress |
| Base58 | Address encoding | ✅ Complete |
| ChaCha20-Poly1305 | Key encryption | 📋 Planned |
| CLSAG | Ring signatures | 📋 Planned |
| Bulletproofs+ | Range proofs | 📋 Planned |

### Core Features

| Feature | KMP | Dart |
|---------|-----|------|
| Standard address | ✅ | ✅ |
| Subaddress | ✅ | ✅ |
| Integrated address | ✅ | ✅ |
| Mnemonic (25 words) | ✅ | ✅ |
| View-only wallet | 📋 | 📋 |
| Transaction building | 📋 | 🔄 |
| Synchronization | 📋 | 📋 |

## ⚠️ Security Notice

This is an **experimental implementation** for educational and research purposes.

**DO NOT use in production** until:
- Full cryptographic audit completed
- All test vectors validated against reference implementation
- Extensive real-world testing performed

## 🧪 Testing Strategy

### Oracle Testing
All cryptographic operations are validated against `monero-wallet-cli` outputs:
- Address generation from known seeds
- Transaction hash verification
- Key image computation

### Test Vectors
Located in `test/vectors/` directories with data from official Monero test suite.

## 📚 References

- [Monero Source Code](https://github.com/monero-project/monero)
- [Zero to Monero](https://www.getmonero.org/library/Zero-to-Monero-2-0-0.pdf)
- [Monero RPC Documentation](https://www.getmonero.org/resources/developer-guides/daemon-rpc.html)

## 📄 License

MIT License - see [LICENSE](LICENSE) for details.

## 🤝 Contributing

Contributions welcome! Please read the design documents in `docs/` before submitting PRs.

---

**Current Monero Protocol Version**: v0.18.4.4 "Fluorine Fermi" (2025-11-19)
