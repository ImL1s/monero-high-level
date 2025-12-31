重寫 Monero C++ 錢包庫為 Kotlin 與 Dart 的可行性報告

參考的 Monero C/C++ 錢包庫及功能模組拆解

我們選定 monero-cpp （Monero Ecosystem 的 C++ 錢包庫）作為重寫參考。monero-cpp 封裝了 Monero 核心的 wallet2 介面，提供完整的錢包功能，是目前功能最完整且常用的 C++ Monero 錢包開發庫 ￼ ￼。其支援的功能模組包括：
	•	錢包與帳戶管理：建立/恢復錢包（從種子、助記詞）、打開/關閉錢包、設定密碼與檔案路徑等。支援觀察錢包（唯讀模式）、離線錢包，以及多簽錢包的初始化與協調 ￼。也提供帳戶（account）和子地址（subaddress）管理：查詢餘額、創建新帳戶或子地址、設定標籤等 ￼ ￼。錢包能匯出助記種子和金鑰（view key/spend key）等，以供備份。
	•	密鑰與地址管理：由種子產生私鑰、公鑰和主要地址，支援多語言助記詞字典和種子轉助記詞 ￼。生成子地址及整合地址（含付款 ID），並能解析整合地址取得內含的付款 ID ￼ ￼（註：付款ID與整合地址在新版本中已不建議使用）。亦支援生成多簽錢包的共同地址與協調多簽金鑰交換 ￼ ￼。
	•	區塊鏈同步與Daemon通訊：透過遠端 Monero daemon（monerod）同步區塊鏈資料。錢包會連接到 daemon 獲取網路高度、區塊資料和交易，然後利用錢包的私密檢視金鑰掃描找出屬於錢包的輸出。monero-cpp 採用 wallet2 核心的同步機制，可選擇全節點或輕節點模式（以 RPC 介面與遠端節點溝通）。功能包含：設定/查詢 daemon 連線資訊、檢查與節點的連線狀態、獲取網路當前區塊高度、同步區塊並提供同步進度回調通知等 ￼ ￼。此外，monero-cpp 也提供對 daemon RPC 常用方法的包裝，如查詢區塊訊息、交易池狀態等，方便與區塊鏈後端互動 ￼。
	•	交易建立與簽署：支援建立各種交易類型，包括一般轉帳、批次轉帳、多重輸出找零（sweep）以及發送所有餘額（sweep all/dust）等 ￼ ￼。透過錢包選擇未花費輸出並結合誘餌輸出（ring）來組成機密交易 (RingCT)，在交易中隱匿金額和發送者。交易簽署會用到 Monero 的環簽章 (CLSAG) 和 Bulletproofs+ 零知識證明（範圍證明）以隱藏金額 ￼ ￼。錢包庫可根據指定的轉帳設定（如收款地址、金額、手續費優先級等）建立交易並完成簽名 ￼。另外支援離線簽名流程：可以匯出交易的未簽名資料，讓離線環境的錢包簽署，再將簽名結果上傳廣播 ￼。簽署過程也包含生成交易機密證明（Bulletproofs）和關聯輸出的關鍵影像 (key image) 等，以保證交易不可雙重花費。
	•	交易查詢與錢包資料管理：提供豐富的錢包狀態查詢 API，可按條件檢索交易、轉帳或輸出。例如可以查詢指定 Tx 哈希的交易詳情，或篩選所有傳入/傳出的轉帳紀錄 ￼。可以取得錢包內未花費輸出（UTXO）列表並匯出/導入輸出資料與 key image，以支援離線錢包同步花費狀態 ￼。支持凍結特定輸出避免被花費 ￼。此外，有 API 查詢交易備註、設定或取得地址簿聯絡人等（例如 get_tx_notes, address book 功能） ￼ ￼。錢包庫也實作各種證明與驗證工具：例如取得交易金鑰 (tx key) 供收款方驗證付款 ￼；生成和驗證付款證明 (Tx Proof) 來證明某筆交易支付給某地址 ￼；產生餘額證明 (reserve proof) 以證明錢包擁有的金額 ￼；簽名/驗證任意訊息（message）以證明地址所有權等 ￼。上述功能確保我們重寫的庫需與官方錢包在資料格式和加密邏輯上完全互通，即產生的交易、金鑰、證明等可被官方工具驗證。
	•	多簽名 (Multisig) 支援：monero-cpp 支援建立 N-of-M 門檻的多簽錢包，以及多簽交易的簽署流程 ￼。這涉及多方交換多簽資訊（如共同種子hex、交換公鑰），最終讓多方各自簽署交易片段並彙集簽名 ￼。重寫時需要涵蓋多簽的初始化（prepare_multisig）、後續交換金鑰（make/exchange multisig keys）、匯出/匯入多簽資料（multisig hex）和最終交易簽名提交等流程 ￼。多簽流程相當複雜，實作需確保與官方演算法一致，否則多簽各方將無法湊成有效簽章。
	•	錢包儲存與安全：將錢包金鑰和資料序列化保存到檔案（.keys 檔），並以使用者密碼加密。官方錢包使用 CryptoNight 慢雜湊從密碼導出金鑰並用 ChaCha20/Poly1305 等進行加解密 ￼。重寫時需注意錢包檔加密方式，以確保安全性和可相容性（至少應提供與官方錢包相同的加密強度）。Kotlin 與 Dart 都可實作類似的 KDF 和對稱加密，但在 Web 平台可能需要利用 Web Cryptography API 或純軟體實作。倘若不直接相容官方錢包檔案格式，也需提供安全的序列化保存機制與變更密碼功能 ￼。
	•	其它輔助功能：包含錢包同步過程的事件通知（例如區塊同步進度、收到款項事件） ￼；啟動/停止挖礦（通知後端 daemon 為此錢包地址挖礦） ￼；帳戶標記與標籤管理，用於將錢包內帳戶分組 ￼；以及錢包屬性設定存取（可存取自定義錢包屬性）等。這些附加功能確保錢包庫在各種應用場景下的完整性，不過相對核心功能而言實現較為簡單。

以上模組構成了 Monero 官方錢包的完整功能集，我們的 Kotlin 與 Dart 實作需逐一覆蓋這些功能，確保與原始 C++ 庫在功能和資料上的對齊與互通。

Kotlin Multiplatform 實作架構建議

1. 架構總體設計：採用 Kotlin Multiplatform (KMP) 來撰寫 Monero 錢包核心邏輯，實現單一套 Kotlin 程式碼同時支援 Android、iOS 以及桌面平台（JVM/原生）。我們將核心功能拆分為數個KMP模組，各模組在 common 主程式碼中實現通用邏輯，在需要與具體平台互動時使用 expect/actual 機制實現。例如，檔案存取、網路通訊等可由各平台 actual 實現，但絕大部分 Monero 區塊鏈和密碼學邏輯可以在共用程式碼中完成，確保所有平台的行為一致。

2. 錢包核心與資料模型：定義 Kotlin 介面和資料類別以描述錢包功能與結構。可以參考 monero-cpp 的 API 規範，設計類似的 MoneroWallet 接口，具備上述各項錢包操作方法，例如 getBalance(), sync(), createTransaction(), getAccounts() 等 ￼ ￼。對應地，定義資料類別如 Account、Subaddress、MoneroTx（交易資料）、MoneroTransfer（轉帳紀錄）、MoneroOutput（輸出） 等，用於封裝錢包的狀態資料。這些類別可實作序列化介面，方便錢包狀態保存。採用 Kotlin 的資料類（data class）便於實現可序列化與拷貝。整體架構上，我們可將核心邏輯獨立為 monero-core 模組，專責金鑰運算、區塊鏈資料處理和交易構建；將網路與存儲細節放在 monero-net、monero-storage 等模組中，透過介面注入核心。

3. 關鍵模組對應實作：根據前述功能拆解，各模組在 Kotlin 中的實作重點如下：
	•	金鑰與助記詞：使用 Kotlin 實作 Monero 的種子生成與金鑰派生算法。每個 Monero 錢包有128位元熵（256位種子經過Keccak hash的一半）產生的種子，經過特定字典轉換為25詞助記詞。我們可將 BIP39 助記詞庫作為參考（或直接使用Monero自帶詞表）來實現助記詞編碼/解碼 ￼ ￼。接著，從種子推導主私鑰：Monero主私鑰包含一對花費金鑰(private spend key)與檢視金鑰(private view key)，檢視金鑰 = H_n(spendKey)（某哈希）所得。這些演算法需用純 Kotlin 編寫或利用可信任的Kotlin多平台函式庫。Kotlin/JVM 可使用現有 Java BigInteger 和 BouncyCastle 等庫，但在 Kotlin/Native 上需替代方案。我們可採用 kotlin-multiplatform-bignum 等庫來處理大整數運算，以支持255位元曲線運算。助記詞以及金鑰生成會實作在 common 模組，確保各平台助記結果一致。
	•	地址生成與驗證：依據 Monero 演算法使用私鑰生成公鑰，再組合成主要地址與子地址。Monero地址是經過 Elliptic Curve Point 衍生及網路前綴的編碼字串（使用Base58Check）。我們將實作地址編碼器，包含主地址 (以4字節網路識別碼+公鑰Hash+校驗碼構成) 和子地址導出。子地址由主私鑰派生：SubAddressPublicKey = H_n(主花費公鑰 + minor_index + major_index) + 主花費公鑰 等複雜步驟，我們需嚴格按照官方算法實現，以確保生成之子地址與官方錢包一致 ￼。我們可以撰寫單元測試，用已知種子比對第一個主地址和第一些子地址與官方工具輸出，以驗證演算法正確性。
	•	區塊鏈同步與RPC：實作 DaemonClient 類別，用於與 monerod 節點通訊。推薦使用 Kotlin 多平台網路庫（如 Ktor client），在共用程式碼中編寫 JSON-RPC 請求發送與回應解析邏輯。支援的 RPC 方法包括：get_height, get_block_header_by_height, get_blocks_by_range 或 get_block 等，以取得區塊資料；get_outputs 或 get_transfers 等查詢輸出 ￼。由於同步區塊需要頻繁調用RPC，我們應優化請求：例如以區塊高度範圍批次獲取區塊或交易資料，以減少網路往返。Kotlin 實作中，可以設計 BlockchainScanner 模組，使用協程 (Coroutines) 在後台序貫同步區塊並解析交易。在Android/桌面上可開新執行緒運行協程；在 iOS (Kotlin/Native) 則需注意不能阻塞UI執行緒，可使用 Dispatchers.Default 啟動。同步時遇到新區塊或新交易，可透過錢包介面的 Listener 回調或 Kotlin Flow/Channel 發出通知 ￼。此模組還需處理重新掃描（rescan）功能：提供重新從區塊高度0或特定高度掃描的方法，清除舊資料重建錢包狀態。
	•	交易構建與簽名：在 Kotlin 中重寫 Monero 環簽章和機密交易演算法是最具挑戰的部分。需要實作下列步驟：1) UTXO選擇：從錢包未使用輸出中挑選作為交易輸入，並從區塊鏈獲取若干無關輸出作為誘餌（組成 ring）；2) 機密金額：對每個輸出金額建立Pedersen Commitment 和 Bulletproofs+ 範圍證明 ￼ ￼；3) 簽章：使用 CLSAG 演算法對每一組 ring 輸入產生連結環簽名（需要計算環中所有公鑰的線性組合和挑戰值）；4) 序列化：將交易組裝為Monero二進位格式（包含簽章、機密證明、鑰雜湊等資料）。為實現上述功能，我們需在Kotlin中建立 CryptoUtils 模組，提供曲線運算和雜湊函數。曲線 Ed25519 上的標量和點運算須要自行實作或使用現有Kotlin對應函式庫（如果有）。目前JetBrains尚無官方 Ed25519 Kotlin/Native 實作，因此可能需要直接翻譯C++密碼演算法到Kotlin。幸運的是，社群已有參考實作：例如 Dart 的 monero_dart 套件已純 Dart 實現了 Bulletproof+ 和 RingCT 全部算法 ￼ ￼。我們可參考類似思路，在Kotlin中撰寫。由於 Kotlin/Native 不能使用JVM現有的高效大數與加密庫，實現時務必注重效能：可透過位運算優化大數模運算，或採用演算法上的優化（如使用window方法加速點乘）。必要時，可在Kotlin/Native 中以C/Cpp實現關鍵部分再透過 cinterop 調用，但這違背「純Kotlin」要求且需在各平台維護多套實作，因此除非效能嚴重不足，盡量以純Kotlin完成。完成交易簽名後，Kotlin庫應提供方法將簽名的交易送出（Relay）：這可以直接呼叫 daemon 的 submit_tx RPC 將序列化交易hex發送到網路。
	•	多簽與離線支援：Kotlin 實作需處理多簽錢包的工作流程，包括：建立多簽 (創建者產生 invite code，共享給參與者)、交換多簽公鑰並計算最終共同地址、導出/導入多簽輸出資料和簽名資料等 ￼。可以設計 MultisigCoordinator 類別，封裝多簽步驟。每步會產生需傳遞給其他簽署人的字串或檔案，例如 prepareMultisig() 產生初始協商字串，exchangeMultisigKeys() 處理他人提供的字串並回傳新的資料 ￼。最終 signMultisigTxHex() 讓每人對交易hex簽名，並收集至足夠門檻後 submitMultisigTxHex() 廣播交易 ￼。在KMP實作時，多簽流程大部分邏輯可共用，但序列化/反序列化和檔案I/O或許各平台不同（檔案I/O 可用Kotlin io API 或Expect實現）。離線錢包支援則體現為兩塊：觀察錢包（view-only）只保存檢視私鑰，可在無花費權限下同步交易；離線簽署錢包保存花費私鑰，在無網環境下簽交易。兩者需要透過匯出輸出和 key image 機制同步花費狀態 ￼ ￼。Kotlin庫應提供方法：exportOutputs()/importOutputs() 以及 exportKeyImages()/importKeyImages() 來讓觀察錢包與離線錢包交換資料 ￼。這部分牽涉序列化輸出清單和簽署 key image 等操作，也需嚴謹實現。
	•	資料保存與安全：Kotlin Multiplatform 可以利用 SQLDelight 或 Realm Kotlin 等方案，實現錢包的資料儲存（如本地資料庫保存交易記錄）和鍵值存儲。也可簡單將整個錢包序列化為加密檔案（官方 .keys 檔類似做法）。建議實作錢包匯出為種子或助記詞，盡量少在應用中存明文私鑰。若需要與官方錢包檔案互通，需實作 CryptoNight 算法以從密碼推導金鑰 ￼，並實現ChaCha20/Poly1305加解密流程。否則，也可採用Argon2作KDF、AES-GCM加密自定義格式，只要告知使用者無法直接匯入官方錢包檔即可。Kotlin/Native 無內建上述算法，我們可用 Multiplatform 庫實作或自行移植演算法。安全隨機數方面，Kotlin 在JVM可用 SecureRandom，Native 和 JS 則需 actual 實作，可能調用平台的 /dev/urandom 或 Web Crypto API 確保隨機性。

4. 平台部署與接口：完成上述核心後，可將 Kotlin 庫通過 KMP 打包：Android 上產生 AAR，iOS 上產生 Framework（透過 Kotlin/Native），桌面可發佈為 JVM 庫或Native執行檔。為方便使用，我們可以提供一層較高階的 Kotlin API 或 DSL。例如提供協程擴充讓所有操作可非阻塞呼叫、提供Observables或Flow匯出同步進度和事件，方便Kotlin客戶端使用。此外，也應考慮到多執行緒同步：錢包可能需要避免同時執行兩個轉帳或同步操作，可在介面方法內部加鎖或序列化操作。另外，記憶體管理在 Kotlin/Native 尤為重要，因其沒有垃圾回收，需避免產生過多中間對象或保留大量區塊資料在記憶中，可考慮分段同步並將已解析的區塊丟棄。

5. 測試考量：Kotlin 部分可針對共用邏輯撰寫單元測試（在JVM運行），使用已知向量驗證。如：給定某助記詞，應產生確定的主地址、公私鑰（可對照 monero-wallet-cli）；對小額交易流程進行端對端測試，對比官方錢包計算的手續費和交易hash等。由於 monero-cpp 已有100多項測試 ￼（monero-java/ts），我們也可參考那些測試案例建立等價的Kotlin測試，確保核心演算法輸出吻合。接下來，針對各平台（Android/iOS）進行集成測試：比如在Android上用我們的庫連接測試網路發送實際交易，確認可以被區塊鏈網路接受。

Dart/Flutter 實作架構建議

1. Dart 實作總覽：在 Dart 中，我們建議以 純 Dart 套件 方式實現 Monero 錢包功能，方便整合進 Flutter 應用。這將成為一個獨立的 Dart 庫，支援 Android、iOS、Web、Desktop 等所有 Flutter 平台，透過相同程式碼提供錢包功能。參考近期發布的 monero_dart 套件，它證明了以 Dart 實現 Monero 密碼學和交易的可行性 ￼ ￼。我們的新 Dart 實作將基於類似的架構：
	•	模組劃分：將功能區分為幾個 Dart 庫模組，例如 monero_wallet, monero_crypto, monero_rpc 等。monero_wallet 管理錢包狀態與操作、monero_crypto 專注實作加解密、monero_rpc 處理與節點/遠端錢包的通訊。由於 Dart 沒有編譯期多平台區分，一套程式碼可跑於各平台，因此我們可在執行時檢測平台（比如瀏覽器環境 vs 手機）作出少量調整（例如存儲路徑、CORS 設定），大部分邏輯無需區分平台。
	•	資料結構與介面：設計與 Kotlin 類似的物件模型。在 Dart，我們可定義 MoneroWallet 類別，包含前述所有錢包操作方法。Dart 的語言特性允許未來將此類封裝成 Flutter 的 ChangeNotifier 或 Bloc 模式以便 UI 綁定，但核心庫應保持 UI 無關。資料類如 MoneroAccount, MoneroTransaction, MoneroOutput 則用 Dart 類別或 record/type 定義。Dart 雖無介面強制實作，但可使用 abstract class 定義 Wallet 接口，讓不同實現共用（例如將來若支援 remote wallet RPC 控制，也可實作相同接口）。monero_dart 套件聲稱支援完整 daemon RPC 和錢包 RPC ￼，我們也可提供類似的 RPC 客戶端類別，方便在需要時操作遠端錢包或取得鏈上資訊。
	•	核心功能實作：
	•	金鑰助記詞：利用 Dart 的 BigInt 類別進行大數計算，實作與Kotlin相同的金鑰生成邏輯。社群已有 Monero 專用助記詞實作，可參考 monero_dart 取得 seed 到助記詞的實現 ￼。Dart 的隨機數生成可使用 Random.secure() 來獲取安全亂數源。助記詞字典可能需要內置 Monero 的詞表 (英文和多語言)，並撰寫編碼/解碼function。接著，用助記詞或種子導出私鑰（同Kotlin步驟），這涉及Keccak哈希（Keccak-256）和 scalar mod l 運算。Dart 可使用 pointycastle 或 cryptography 套件取得 SHA3/Keccak 實作，以及Ed25519基本操作。但由於Monero使用特殊的 Ed25519 衍生，我們可能仍需自行處理曲線上計算（如共用祕密計算、子地址派生）。不過，Google的 cryptography 套件提供了不少演算法，包含 X25519, Ed25519, SHA-3 等，我們可以加以利用，以避免從零實現所有基礎。需要注意的是，cryptography 套件在不同平台底層實現可能不同（如在Web上使用Web Cryptography API，在Native上用優化實現），這有助於性能提升，同時保持我們程式碼純 Dart 調用。
	•	網路與同步：在 Flutter/Dart 環境下，我們可使用 http 套件或 dio 發出 RPC 請求。建議封裝一個 MoneroRpcClient 類別，可設定節點位址和認證，方法如 getBlockHeaderByHeight(height), getBlocks(startHeight, count) 等，返回解析後的資料結構。Dart JSON 解析相對容易，但需要注意 big integer 要用字符串處理以避免精度丟失。同步過程上，可使用 Isolate (Dart的多執行緒) 來執行區塊掃描，避免阻塞主執行緒。具體做法是將區塊下載與解析的任務丟給背景 isolate 運行，通過 ReceivePort 發送進度消息給UI isolate。這樣可在Flutter界面上顯示同步進度。monero_dart 亦提供了解析交易和區塊的功能，可供參考 ￼。我們的 Dart 實作中，同步模組會負責：儲存已同步高度、迭代請求塊資料並交給 錢包核心 驗證輸出是否屬於錢包。由於 Dart 的 speed 可能不及原生C++，同步時要控制每批處理的大小，或實現增量同步（每次啟動從上次停止高度繼續）。可以實作檢查點或使用輕量模式：例如利用 Monero light wallet server API（如果需要減輕瀏覽器端負擔），不過這將引入對第三方服務的依賴與信任降低。理想情況下，我們的 Dart 實作也應能完全在客戶端解析區塊。
	•	交易建立與加密：Dart 端同樣需要重現CLSAG簽章和Bulletproofs演算法。好消息是，已有 monero_dart 這樣的套件直接提供「建立、簽署 RingCT 交易（含Bulletproof+）的純Dart實作」 ￼。我們可以參考 monero_dart 的做法或直接使用其部分程式碼/邏輯（考慮授權情況下）。據 Medium 專文介紹，monero_dart 可以「建立、簽署(Bulletproofs+)並產生交易證明，包含多重簽署交易，全部以純 Dart 完成」 ￼。這證明了Dart 完全可實現這些高複雜度的密碼學。我們仍需特別注意效能問題：Dart 的 BigInt 為不定長度整數，進行數百次的大數模運算會較C++慢。但 Dart VM/AOT對BigInt有優化，且monero_dart已經測試運行可行。必要時，可啟用 dart:ffi 調用Native函式庫或 WebAssembly 以提升性能 ￼（例如調用Rust或C的Bulletproofs實現），但這違背純Dart原則且增加平台相依性。因此除非在Web平台性能無法接受，否則盡量以 Dart 編寫核心算法。我們可以提供選項：例如在Web環境偵測若存在 WebAssembly 加速檔可載入，以加快驗證；在移動端則使用純Dart。交易構建時，會從錢包物件中取得未花費輸出，組裝 ring，計算假名地址等。Dart 可以採用與Kotlin類似的模組化：將簽章算法置於 RingCtSignature 類中實作，Bulletproofs 則在 RangeProof 類實作，使代碼清晰。同時加上單元測試：驗證產生的簽章可以透過官方daemon的 verify 接口驗證（monerod 有verify_tx RPC可供開發階段使用），或與 monero_dart 的結果比對。
	•	錢包邏輯與高階API：在核心演算法之上，實作易於使用的錢包介面。例如 wallet.sync() 內部調用 RPC 獲取區塊 + 掃描，wallet.sendTransaction(address, amount) 內部會組裝 MoneroTxConfig、調用前述簽章模組，完成後自動 relay. Dart 是單執行緒模型，為避免UI卡頓，我們可以讓這些高延遲操作返回 Future，在未來完成時通知結果。開發者使用時配合 async/await 即可避免阻塞UI。此外，可使用 Stream 來定期發送同步進度、發現新交易等通知；使用 StreamController 在錢包後端有事件時 add。一些非核心但便利的功能也可提供：例如 URI 生成/解析（將付款請求轉為 monero:// 協議 URI，或解析他人提供的URI） ￼、地址簿（保存在本地，以便轉帳時調用）等。這些在 Dart 實作上難度低，可以與 Flutter 前端協作增強易用性。
	•	多簽與離線模式：Dart 的多簽流程可參照 Kotlin 部分，同樣需要一系列交換字串和簽名組合。可以實作 MoneroMultisigWallet 類別繼承一般 MoneroWallet，增加如 prepareMultisig(), joinMultisig(...) 等方法。藉由monero_dart宣稱支援多簽交易簽署 ￼, 我們可確信 Dart 能處理複雜的簽名累加。實作時注意，每位簽署者的狀態需儲存（如簽署者暫存的其他簽名資料）。這可以序列化成JSON或二進制讓用戶自行傳輸。離線/觀察錢包在 Dart 環境下也可實現：觀察錢包不持有spend key，因此 wallet.sendTransaction 等應該被禁用或拋錯；但觀察錢包可以使用 exportOutputs() 等將看過的輸出給出。離線錢包則相反，不連網但持有spend key，可接收別人提供的輸出資料更新狀態後簽交易。這些流程跟官方一致才能互通。
	•	存儲與狀態維護：Flutter 移動端可使用 path_provider 獲取安全路徑，將錢包資料（如種子字串或加密後種子）保存在裝置。例如可將seed以密碼加密後存在 device KeyStore 或 Keychain（透過現有plugin）。也可以簡單地讓用戶每次輸入密碼載入助記詞，不長期存任何敏感資訊，提升安全。桌面端可將資料存於使用者主目錄的配置檔。Web 平台則可考慮使用 IndexedDB 或 LocalStorage 保存加密後的錢包資訊，以在刷新頁面後恢復錢包，不過需注意存儲空間限制和安全（LocalStorage容易被惡意JS存取，故應加密）。Dart 沒有內建加解密，我們可使用 cryptography 套件的 AES-GCM 或 Chacha20 來對錢包種子/私鑰進行對稱加密保存。由於 monero-wallet-cli 本身不支援 web，所以我們可以選擇自己的安全方案而無需與官方錢包檔兼容，但加密強度不能降低。

2. Flutter 平台整合：有了 Dart 核心庫，我們可以輕易將其融入 Flutter 應用。對 Android 和 iOS，Dart 執行時沒有額外相依；對桌面，Flutter 程式也能直接呼叫 Dart 方法執行重度運算。要注意的是，如果不使用 isolate，Dart 的所有計算都在UI執行緒上執行，容易卡頓。因此在 Flutter 中，我們可能提供「後台模式」：例如一個 WalletSyncIsolate 類別，啟動一個獨立isolate執行同步，完成後將錢包狀態傳回主執行緒。這需要序列化傳遞資料（可以傳基本類型或將錢包差異最小化傳輸）。另一種方式是在 Dart 層面盡量使用 compute() 或 isolate.spawn，對使用者而言簡化操作。我們的庫應該盡量封裝細節，例如提供 wallet.startSync() 非同步啟動同步，內部已經使用 isolate，不讓開發者額外操心執行緒管理。

3. Dart 實作的挑戰與風險：
	•	性能瓶頸：Dart 相對C/C++執行速度較慢，特別是 Web 平台上轉譯為JavaScript後，運算大量密碼學可能緩慢。因此可能的風險是同步初始區塊鏈非常耗時。在Mobile端，monero_dart已成功在Stagenet運行，說明性能尚可，但Web端同步整個區塊鏈幾乎不可行（數GB資料）。因此在Web上要慎重考慮同步策略（後續詳述）。我們可在文檔中建議使用者在Web環境下連接輕量級服務或限制同步範圍。
	•	安全性：用Dart重寫需非常小心避免安全漏洞，例如整數溢位、隨機數質量、記憶體洩漏（雖有GC但仍要防止敏感資料長期駐留記憶體）。尤其在Web中，程式碼暴露給使用者，一定程度降低安全（但Monero是非託管錢包，私鑰在客戶側其實更安全，只是任何XSS攻擊都可能窺取JS記憶體）。因此要指導開發者在Web部署時注意內容安全策略、避免第三方腳本引入等。
	•	相容性：我們要確保 Dart 產生的交易與官方完全相容。這意味著要和Kotlin版本一樣，使用相同測試向量檢驗。例如交易簽名的結果（如tx hash、key image）應與官方錢包計算吻合。若有任何細微差異，可能導致交易無效或無法被鏈上承認。這需要強大的測試來降低風險。

總之，Dart 實作將利用 Flutter 的跨平台優勢，在UI上提供一致的錢包行為，同時後端運算需優化以克服Dart層性能限制。monero_dart 套件的出現 ￼表明社群已部分攻克這些挑戰，未來我們可以站在其肩膀上完善剩餘功能。

跨平台密碼學相依與潛在缺失實作

Ed25519 與 Curve25519 運算：Monero 使用 Ed25519 曲線作為核心（如密鑰、地址生成）以及衍生的共用密鑰運算。Kotlin 和 Dart 本身沒有內建 ed25519 庫，需要自行實現或引入第三方。Kotlin/JVM 可調用 JVM 提供的 Ed25519 簽名（Java 15+支援）但 Kotlin/Native 沒有，同理 Dart 需自己算。這涉及大數模 l (≈2^252) 和曲線點的加法、標量乘等。我們需實作高精度大數運算（mod l 和 mod p），如前所述 KMP 可用大數庫 Ion-Spi​​n BigInt ￼, Dart 則直接有 BigInt。曲線點運算可採用 Edwards曲線的公式，確保恆等性和安全（如防止時間測量攻擊，必要時做常數時間運算）。

Keccak-256 / 哈希算法：Monero使用 Keccak-256 作為哈希（CryptoNight PoW也基於Keccak，但PoW我們不需要）。Keccak (即SHA3) 在很多庫都有實作。Kotlin若無對應庫，可直接從標準規格實現或找KMP哈希庫。Dart的 pointycastle/cryptography 亦提供 SHA3 實現。還有 Monero 特有的哈希：例如生成 key image 用到 Ki = x * Hp(Pi) 等，一些需要自行寫。總之哈希部分相對簡單，有現成實現可以利用。

環簽名 (CLSAG)：這是 Monero 交易隱私的關鍵。CLSAG（Concise Linkable Spontaneous Anonymous Group）是 2020年引入的演算法，比舊版MLSAG簽名更高效。我們需實現CLSAG的簽章生成與驗證：包括計算消息雜湊、隨機挑戰、多個響應值以及 link tag（關聯標記，用於識別同一 key image 的雙花）。CLSAG 論文與Monero原始碼可以參考，但要完全以 Kotlin/Dart 重現正確性有難度，需要細心對照。monero_dart 已實作CLSAG，因此可與其結果比對驗證。我們也可以使用官方daemon的 verify_signature 等工具在測試時驗證CLSAG簽名。

Bulletproofs+ 範圍證明：Monero現行使用 Bulletproofs+ 來證明輸出金額非負且守恆 ￼。Bulletproofs+ 是複雜的零知識證明，需要大量椭圓曲線內積、生成隨機承諾及Pedersen承諾計算。這明顯是實作重點之一。我們可能考慮三種方案： ￼提到：
	1.	使用純語言實作（monero_dart 就是範例，它已實現Bulletproof+ ￼）。我們可以借鑑 monero_dart 的算法，把等價程式碼轉成 Kotlin。Dart版本也可直接使用monero_dart（若我們自己寫dart，或fork其碼納入）。
	2.	使用 FFI 封裝高效C/C++/Rust庫。例如 Rust 社群的 bulletproofs（dalek 區塊鏈庫） ￼或 Monero C++ bulletproof程式碼 ￼。透過JNI/FFI在Kotlin或Dart呼叫，可極大提升性能。但此法違背純語言實作的要求，並增加移植工作量（各平台均要編譯提供native函式庫）。
	3.	WebAssembly：將Bulletproof核心編譯成WASM模組，Kotlin可在JavaScript環境下透過JSInterop使用，Dart則透過 dart:js 調用。然而Kotlin/Native和Android無法用WASM，Dart本地也不需要WASM（只有Web可能用），此方案僅對Web有益。

綜上，建議優先嘗試純 Kotlin/純 Dart 實作 Bulletproofs+，除非後期測試發現移植WASM在Web必要。Bulletproofs演算法非常複雜，需高度小心：包括生成多項隨機數、計算多個承諾後進行多輪內積證明。我們應該充分利用Monero官方測試向量（Monero原始碼或Monero研究實驗室可能提供一些Bulletproof範圍證明測試），或自行使用官方錢包產生一些交易的range proof，來驗證我們的實現正確性。

多重簽名協議：Monero多簽包含一系列非對稱加密協商步驟：交換產生共通公鑰、一致化簽名的聚合等。這涉及生成和合併多個部分密鑰、計算聯合公鑰並確保每方掌握正確私鑰片段。官方曾多次修正多簽協定（v1, v2版本差異），我們需跟隨最新（目前v2）。多簽過程使用到Lamport密鑰對做共享隨機數交換以及 MLSAG 演算法在多簽環境下的應用。如果找不到詳細文檔，我們可能需要直接研讀 monero 原始碼 (wallet2.cpp 多簽部分) 來模仿實作。這是高風險區域，一點實現不當，就會導致多簽錢包無法成功完成交易或出現安全漏洞（例如某簽名者可推知他人密鑰）。我們應在功能表中標記多簽為進階功能，或許優先完成單簽功能後，再集中精力測試多簽。

隨機數與隨機Oracle：Monero簽名需要安全隨機數（例如環簽名中的 alpha、Bulletproofs中的blinding等）。在 Kotlin 中要確保各平台都用真隨機熵來源。JVM上可調用 SecureRandom, iOS上可以使用 arc4random_buf, Web上可用 Crypto.getRandomValues（可能透過Kotlin/JS interoperability）。我們可為隨機數撰寫 expect/actual，封裝統一接口。Dart 則簡單使用 Random.secure() 即可，它已經調用操作系統安全隨機源。在多簽中，各成員需各自生成隨機數並交換，我們也需確保每次執行都新生成，不可重複使用（否則會洩漏私鑰）。我們應實作 nonce 的緩存以防重放攻擊，以及在簽名過程中將關鍵隨機數置0從記憶體抹除等操作，加強安全。

尚缺的原生實作：目前 Kotlin/Native 平台在一些演算法上或缺少高性能實作。如AES加解密可使用OpenSSL但我們不想依賴native。因此若需AES（例如錢包檔加密），我們得用Kotlin實現AES（可以考慮使用Kotlin版的 AES，或簡單採用XSalsa20/ChaCha20等 stream cipher，因其較易實現且速度快）。Dart有純Dart的加密庫，因此問題不大。另一個缺漏可能是RandomX（Monero新PoW算法）沒有純Kotlin/Dart版本，但我們錢包不需要PoW計算，只需Daemon挖礦時RPC指示，所以可忽略。總之，大部分Monero相關加密我們都能以純代碼解決，只是在效率和工作量上要權衡。如果遇到實在難以重現或效能糟糕的部分，我們可以在工程上提供可插拔的實現，例如允許開發者選擇性地透過動態庫/WASM提供替代實現——這樣核心仍是我們的代碼，只是將重負載部分交由外部模組，加速但不改變結果。

Flutter Web 平台支援與限制說明

將 Monero 錢包功能搬上 Web（即 Flutter Web 或純 Dart Web 應用）需考慮一些特殊限制：
	•	運算性能與執行緒：Web 環境下，Dart 代碼被編譯為JavaScript執行。JavaScript在瀏覽器中單執行緒運行，重度計算會阻塞UI。雖然可以使用Web Worker改善，但 Flutter Web目前Isolate的支援存在限制（Dart2.17+已經支持Spawn isolate在Web做為Worker，但使用上需注意傳輸成本）。我們的實作若直接在主執行緒同步區塊幾乎不可行，可能導致頁面長時間凍結。因此在Web上強烈建議使用Isolate進行區塊同步和交易簽名驗證等任務，在背景執行並透過消息將結果傳回主執行緒。由於Web Worker無法直接操作DOM，不會阻塞UI。另外，可利用瀏覽器的本地加密功能：例如 WebCrypto API 提供 SHA-256, SHA-3, AES 等硬體加速運算。但遺憾的是 WebCrypto並不直接支援Ed25519或Curve25519運算（截至2025仍不標準化）。所以我們能用的僅哈希和對稱加密部分。Bulletproofs、環簽章只能純JS算或WASM算。倘若我們已經有WASM模組（例如將monero核心編成wasm），可在Web偵測並載入，以極大提高速度 ￼。這屬於可選優化，不影響功能正確性。
	•	記憶體與儲存：瀏覽器對Memory管理嚴格限制。例如單個Web Worker能用的記憶體有限（取決於裝置，數百MB上限）。同步Monero全鏈（數GB資料）在Web基本不現實。因此web端應避免完整同步。可行策略包括：連接遠端錢包RPC服務器（由服務端替你同步區塊，只傳結果）；或使用輕錢包API（如MyMonero的api，提供由服務端過濾後的輸出）。若仍堅持瀏覽器端同步，需要讓使用者明白須連接自己的monerod並長時間開著頁面。針對短期使用的web錢包，我們可只同步近期區塊或只在需要時查詢指定交易。舉例：View Only web wallet 可以要求使用者輸入交易哈希以查看，然後透過RPC抓該交易驗證，無需全鏈掃描。再者，Web本地存儲空間有限（LocalStorage通常5-10MB），IndexedDB雖可放更多但仍不適合GB級。故我們應當限制Web版的錢包用途：如用於小額付款、臨時地址生成，而不鼓勵用來管理大量交易的長期錢包。
	•	網路請求與安全：瀏覽器對跨域請求有CORS限制。monerod默認不設置CORS header，導致從網頁JS直接調用其 RPC 會被瀏覽器攔截（除非daemon配置了 Access-Control-Allow-Origin:*）。如果我們的web錢包需要連接公共節點，必須選擇那些開啟了CORS的服務（社群有些公共RPC node開了CORS，或可透過反向代理解決）。此外HTTP vs HTTPS：若我們的網頁在HTTPS域名上，則只能調用HTTPS的RPC端口（除非用http://localhost在開發模式）。這意味著連接遠端節點要確保它有SSL或使用ws://之類安全通道。為簡化，我們可提供一個可設定RPC代理的機制，例如用戶可在web錢包中輸入一個代理服務URL，該服務器再轉發給實際monerod RPC，從而繞過CORS（因同源）。這屬部署層面的挑戰，不直接影響我們庫的代碼，但需要在文檔中註明。
	•	使用者體驗：Web錢包需要在瀏覽器內保持運行以同步，新開頁面或刷新會重置JS狀態。因此我們要在離開或刷新前提醒用戶備份錢包。也可透過 Service Worker 等技術做些持續運行，但複雜度高。簡單的方式是在 IndexedDB 中保存同步進度和部分鏈資料（如已掃過的output鍵值），下次打開時快速恢復。但這又增加我們庫的實作複雜性。視項目需求決定，早期可不做持久化，每次載入重新同步（反正web用途偏輕量）。未來可優化存儲以縮短重訪等待時間。
	•	功能限制：除了性能因素，某些功能在Web上可能不適用。例如礦工挖礦 (startMining) 需要本地daemon支援，瀏覽器無法啟動daemon，只能調用遠端daemon的挖礦RPC，但一般公共節點不允許，所以此功能在web上基本無用，可以選擇性地在UI隱藏或不實現。在我們庫中可保持接口存在但在web環境直接報錯告知不支援。再如檔案I/O操作（導出檔案保存）在Web上需通過文件下載API，不像移動端能直接寫文件。因此 wallet.save() 在Web可轉為觸發用戶下載錢包備份檔案（JSON或blob），由用戶自行保存。wallet.open(path) 則不適用，只能讓用戶上傳備份檔案再解析載入。

總之，Web 平台可以支援 Monero 錢包，但需做出取捨：傾向輕量應用場景，利用 Web 的特性（無需安裝即可使用）進行小規模、低頻交易或只讀查詢。同時在文件和計算上做出相應限制與提示。例如，可在Web版錢包中明確告知：「此應用不會同步整條區塊鏈，而是依賴輕節點，可能略降低隱私。」或「請勿在網頁中管理大量資金」。這樣比較符合Web環境的安全與性能現實。

測試與開發建議：測試網、RPC 模擬器與模組化開發

為了確保我們重寫的 Kotlin 與 Dart 庫功能正確且穩健，建議採取以下測試和開發步驟：
	•	利用 Monero 測試網路：Monero 官方提供 Stagenet（舞台網）和 Testnet 兩種測試鏈。Stagenet 與主網運行相同版本協議，但使用沒有價值的假幣，適合開發者或用戶在不危及真實資產的情況下試驗錢包和交易 ￼。Testnet 則是開發者用於測試未來硬分叉功能的實驗性網路，可能不穩定且需自行編譯最新代碼才能接入 ￼ ￼。對我們的應用而言，Stagenet是最理想的測試環境 ￼ ￼：我們可以在本地啟動一個 Stagenet 的 monerod 節點（或使用社群公開的 Stagenet 節點），然後使用假幣進行各種操作測試（如轉帳、同步、備份還原）。Stagenet上也有水龍頭提供免費的測試幣 ￼，而且挖礦難度極低，幾分鐘就能出塊 ￼，方便我們產生交易歷史供錢包測試同步。建議在開發過程中搭建本地私有網來快速迭代測試：Monero支持透過啟動daemon時指定 --regtest 來啟動一條私有鏈，我們可在該模式下瞬間產塊並模擬各種情況（雙花、回滾等）進行測試。若regtest模式不可用（Monero目前無正式文件指示regtest，但可用Testnet模擬），可啟動兩個daemon節點組成獨立網路，也能達到類似目的。
	•	RPC 模擬與日誌對比：在開發早期，完全以我們的實作執行所有操作可能難以及時發現問題。因此可採用對比測試：例如在某些操作上，同時使用官方 monero-wallet-rpc 做對比。我們可以啟動官方錢包RPC，載入同一個測試錢包，然後在我們的庫中執行操作（如查詢餘額、創建交易），將結果與wallet-rpc返回的結果比較。例如查餘額是否一致、交易構造出來的手續費/輸出是否一致。如果有差異，說明實作有問題。此方法需要編寫一些測試腳本，用我們庫調用後再調用官方RPC核對。這有點類似將官方RPC作為模擬器或對照組。由於monero-wallet-rpc基本覆蓋了wallet2的所有功能 ￼（官方聲明 wallet-rpc 等同完整錢包邏輯通過RPC暴露 ￼），因此非常適合做我們實作的標準。當然，這需要我們對RPC接口足夠熟悉，但Monero官方文檔詳細列出了daemon RPC和wallet RPC調用 ￼ ￼，可加以利用。
	•	模組化漸進開發：按照功能模組拆解順序逐步實現和測試：
	1.	密鑰/地址：先從種子->主鍵->地址的生成開始。在此階段，使用已知助記詞驗證地址是否正確（Monero官方有固定的測試助記詞對應地址列表，可從社群資料獲取）。確保多種網路類型 (Mainnet/Stagenet/Testnet) 的地址前綴計算正確 ￼。
	2.	RPC 介接：實作基本的 daemon RPC 調用，測試連接本地主機monerod取得高度、區塊哈希等 ￼。同時構建交易資料類別，嘗試從RPC取得某區塊或交易JSON並解析到我們的資料模型，看能否正常表示。
	3.	鏈同步：從高度0開始同步Stagenet鏈，觀察性能和正確性。可先僅同步區塊頭，之後再解析交易輸出。同步過程中打印日誌與monero-wallet-cli同步日誌對照，看高度推進是否一致。如果速度慢，可嘗試優化批量請求或使用get_blocks.bin介面（若我們願意解析binary，可提高效率）。Kotlin和Dart各自對此進行Profiler分析，找出瓶頸（例如BigInt計算或JSON解析），針對優化。
	4.	交易發送：在測試網給錢包充值一些測試幣後，嘗試用我們庫創建一筆轉帳交易但先不廣播，將簽名后的交易hex與monero-wallet-cli創建的交易hex比較。如果完全一致，表示我們實作正確。如果不一致，需分析差別（手續費計算、輸出順序、簽名值等）。調整後再嘗試實際 relay 廣播，看區塊鏈是否接受並出現在目標地址。建議從簡單場景測起：1個輸入->1個輸出的交易，逐步到多輸入、多輸出、找零地址不同等複雜情況。
	5.	多簽和離線：待單簽穩定後，再實現多簽協議。可先嘗試2-of-2多簽，準備兩個我們庫建立的錢包，模擬兩方交換資料並創建一筆共同簽署交易，看能否成功。離線錢包則測試export/import流程：用觀察錢包掃描到一些輸出，匯出給冷錢包簽名，再將簽名提交。整個過程是否與官方CLI結果一致。如果我們在測試中碰到困難，可以查閱Monero社群的一些工具或模擬實現（如monero-python對多簽的處理 ￼）來校正。
	•	可用工具與資源：
	•	Monero官方資源：Monero StackExchange和Monero Docs中有許多實用的技術解釋和範例 ￼。例如 moneroexamples 提供錢包與daemon互動的程式碼片段 ￼，對理解RPC結構有幫助。
	•	社群庫與先前實作：除了monero_dart，還有 monero-python ￼、monero-rs ￼ 等實現，可參考其處理邏輯。monero-python 是完整的Python實作（基於pycryptonight），能輔助我們理解正確算法步驟。monero-rs 則有序列化和金鑰演算法，可對照使用。儘管語言不同，但密碼學本質相通，我們可以驗證關鍵步驟（如子地址 derivation）在不同實作中的輸出是否一致，以增進信心。
	•	測試向量：尋找或產生Monero官方的測試向量，包括：助記詞<->種子<->私鑰<->地址；已知輸入/輸出計算的環簽名挑戰值；Bulletproof範圍證明示例等。如果官方沒有公開，我們可自行用C++核心生成一些（編寫小程式利用wallet2函式輸出中間結果）。有了這些對照數據，可以大幅降低我們實作出錯的概率。
	•	持續整合：將 Kotlin 和 Dart 庫的單元測試納入CI管線，並設置在多平台環境下跑（例如使用 GitHub Actions 在 Linux/macOS/Windows 上執行Kotlin測試，在Chrome headless執行Dart web測試等）。每次修改都跑全套測試，確保不出現回歸。
	•	分階段交付：如果專案允許，優先完成Kotlin Multiplatform版本或 Dart版本之一，以提供一個基準實現，再將經驗應用到另一語言實現上。由於Kotlin和Dart最終邏輯需對齊，不妨先讓其中一個通過全面測試，再同步到另一個，這樣可以避免兩邊各自出錯且難以定位。此外，兩套實作獨立開發時，最好定期交叉驗證：例如用相同種子在Kotlin和Dart版各產生10個子地址，比較結果應完全相同；用相同輸入輸出由兩邊分別構建交易，看Hex是否一致。這種雙重驗證可視為互相模擬：哪邊結果異常就需要調查。
	•	風險點：最後總結一些可能的風險：
	•	區塊鏈協議升級：Monero定期硬分叉引入新功能（例如將來Seraphis/Caszacs方案），我們的實作需持續關注官方更新並更新算法，否則可能產生不相容交易 ￼。特別在硬分叉時，需提早適配新的ring大小、簽章方案等。
	•	隱私與匿名集：我們的實作若有任何微小不同（比如亂數種子處理、輸出排序）都可能破壞匿名性，使交易可以被區分。因此一定要完全遵從官方默認策略，如挑選誘餌輸出的演算法、混合因子、交易extra欄位格式等，保持“官方钱包無法區分由我們庫發出的交易”作為目標之一。
	•	資安審計：由於我們重寫大量加密程式碼，可能引入未知漏洞。建議在發布前尋求社群審查或資安人員審計關鍵演算法實現，以確保沒有明顯漏洞（例如隨機數可預測、內存未清除導致私鑰殘留等）。

綜上所述，通過合理的模組劃分和漸進開發測試策略，我們有信心將現有 C++ Monero 錢包庫的全部功能重現在 Kotlin Multiplatform 與 Dart 環境中。雖然挑戰巨大，但參考既有實作（如 monero-cpp、monero_dart） ￼ ￼並借助測試網驗證，每個功能模組均可逐步攻克。最終產出的兩套庫將功能完整對齊，互相獨立實現，但都能與官方Monero網路與錢包生態無縫互通，為多平台應用開發提供強大的基礎。

來源： Monero 官方開發文檔與社群資源 ￼ ￼、Monero-Cpp/Monero-Java 規格 ￼ ￼、Monero Dart 套件說明 ￼ ￼、以及相關技術論述。