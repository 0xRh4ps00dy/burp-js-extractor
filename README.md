<p align="center">
  <img src="assets/banner.jpg" alt="Burp JS Extractor Banner" width="100%" />
</p>

# Burp JS Extractor

A lightweight **Burp Suite** extension built with the **Montoya API** to passively collect, preview, and export JavaScript file URLs and paths from a target host's HTTP traffic and Site map.

Designed for bug bounty hunters, penetration testers, and security researchers who need a fast, clean way to gather JavaScript endpoints for further static analysis, secret scanning, or endpoint discovery.

---

## Features

- **Dedicated `JS Collector` Tab:** Integrates cleanly into Burp Suite's UI.
- **Target Host Filtering:** Specify a target host (e.g., `app.example.com`) to focus collection on in-scope assets.
- **Passive HTTP Traffic Interception:** Automatically captures JavaScript resources from HTTP responses when:
  - The URL ends with `.js`, or
  - The `Content-Type` header indicates JavaScript.
- **Site Map Context Menu Integration:**
  - Right-click any host or selection in **Target -> Site map** -> `Extensions` -> `Preview JS paths (JS Extractor)`.
  - Loads only JavaScript paths (`.js`, including query strings) for the selected host into the preview panel.
- **Real-Time Preview & Clipboard Export:** Review collected JS paths before copying them to the clipboard (`Copy Preview`) or exporting them to a `.txt` file in your home directory.

---

## Recommended Workflow

### Option A: From the `JS Collector` Tab
1. Open the **`JS Collector`** tab in Burp Suite.
2. Enter the target host (e.g., `app.example.com`) and click **`Load JS Preview`**.
3. Review the detected JavaScript paths from the Site map in the preview panel.
4. Click **`Copy Preview`** to copy the list to your clipboard.
5. The **`Host`** field automatically updates to match the source host of the preview.

### Option B: From the Site Map
1. Navigate to **`Target` -> `Site map`**.
2. Right-click on the target host -> **`Extensions`** -> **`Preview JS paths (JS Extractor)`**.
3. Switch back to the **`JS Collector`** tab and click **`Copy Preview`**.

---

## Requirements

- **Java 17+**
- **Burp Suite** (Community or Professional) with Java extension support (Montoya API `2024.12`)
- **Maven 3.8+** or **Gradle 8+** (if building from source)

---

## Installation

### Pre-built JAR (Recommended)
Download the latest `burp-js-extractor-1.0.0.jar` from the [Releases](../../releases) page.

### Build from Source

#### Option 1: Maven
```bash
mvn clean package
```
Output: `target/burp-js-extractor-1.0.0.jar`

#### Option 2: Gradle
```bash
gradle clean jar
```
Output: `build/libs/burp-js-extractor-1.0.0.jar`

#### Option 3: `javac` + `jar` (No Maven/Gradle)
```bash
mkdir -p lib out
curl -L -o lib/montoya-api.jar https://repo1.maven.org/maven2/net/portswigger/burp/extensions/montoya-api/2024.12/montoya-api-2024.12.jar
javac --release 17 -cp lib/montoya-api.jar -d out $(find src/main/java -name "*.java")
jar --create --file burp-js-extractor-1.0.0.jar -C out .
```
Output: `burp-js-extractor-1.0.0.jar`

---

## Loading in Burp Suite

1. Go to **`Extensions` -> `Installed` -> `Add`**.
2. Set **Extension type** to **`Java`**.
3. Select the compiled `burp-js-extractor-1.0.0.jar` file.
4. Open the **`JS Collector`** tab, enter your target host, and click **`Load JS Preview`**.

---

## Note

This extension passively collects JavaScript URLs and paths observed in traffic passing through Burp Suite and its Site map. It does not perform active crawling on its own.

---

## Disclaimer

This tool is intended **for authorized security testing, bug bounty hunting, and educational purposes only**. You are solely responsible for ensuring that you have explicit permission to test any target systems. The author assumes no liability for any misuse or damage caused by this software.

---

## License

This project is licensed under the **MIT License** — see the [LICENSE](LICENSE) file for details.
