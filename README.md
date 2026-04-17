# IIQ Rule Helper

An IntelliJ IDEA plugin for IdentityIQ (IIQ) rule development. Provides server upload and diff comparison for IIQ rules written as Java classes.

---

## Features

- **IIQ Rule Import** — Scan eligible classes, select what to upload, and push to the IIQ server via REST API
- **Create Backup** — Optionally back up the current server version before overwriting
- **Compare with Server** — Diff your local rule against the live server version side-by-side

---

## Requirements

- IntelliJ IDEA 2023.3 or later
- Java/Kotlin project containing IIQ rule classes
- IIQ server accessible over HTTP/HTTPS

---

## Run Configuration: IIQ Rule Import

Add a new run configuration of type **IIQ Rule Import**.

| Field | Description |
|---|---|
| **URL** | IIQ server base URL (e.g. `https://my-iiq-server`) |
| **Username** | Basic Auth username |
| **Password** | Basic Auth password |
| **Ignore SSL** | Skip certificate validation (for dev/test environments) |
| **Base Packages** | Comma-separated list of packages to scan (e.g. `com.example.rules, com.example.tasks`) |
| **Template Directory** | Directory containing template files (see [Templates](#templates)) |

### Marking a Class for Upload

Add the following field to any Java class you want to make eligible for upload:

```java
private static final boolean PERFORM_IIQ_SERVER = true;
```

The class must also declare the rule name and type:

```java
private static final String name = "My Rule Name";
private static final String type = "RequestObjectSelector";
```

### Upload Flow

1. Run the configuration — the plugin scans all classes under the configured base packages
2. A popup lists all eligible classes with checkboxes and a search field
   - Click the checkbox in the column header to select / deselect all
3. Optionally check **Create Backup** to save the current server version as `{name}-yyyyMMddHHmmss` before uploading
4. Click **Confirm** to upload

Each upload logs one or two lines to the Run console:

```
  [Backup] <Rule name="MyRule-20240418120000" ...> → POST https://.../rest/debug/Rule
  [Upload] <Rule id="abc123" name="MyRule" ...>    → POST https://.../rest/debug/Rule/abc123
```

### Source Assembly

The plugin assembles the rule source in this order:

1. **Imports** — all import statements from the file
2. **Helper methods** — methods called by `execute()`, collected recursively (same class only)
3. **Execute body** — body of the `execute()` method

Leading indentation is normalized automatically.

---

## Templates

Each rule type requires a template file in the configured template directory.

**File naming:** `{type}.template.xml`

Example: `RequestObjectSelector.template.xml`

```xml
<?xml version='1.0' encoding='UTF-8'?>
<!DOCTYPE Rule PUBLIC "sailpoint.dtd" "sailpoint.dtd">
<Rule language="beanshell" name="$TEMPLATE.NAME" type="$TEMPLATE.TYPE">
  <Source></Source>
</Rule>
```

| Placeholder | Replaced with |
|---|---|
| `$TEMPLATE.NAME` | Value of the `name` field in the class |
| `$TEMPLATE.TYPE` | Value of the `type` field in the class |

When uploading to an existing rule, the plugin automatically injects `id`, `created`, and `modified` attributes into the `<Rule>` tag. The `<Source>` block is wrapped in `<![CDATA[...]]>`.

---

## Compare with Server

Right-click any eligible Java class (in the editor or project view) and select **Compare with Server → {config name}**.

- The plugin fetches the current rule XML from the server
- Builds the local XML using the same template and source assembly logic
- Opens a **modal diff** showing the `<Source>` content only, with Java syntax highlighting
- **Left side:** Local | **Right side:** Server

> The class must have `PERFORM_IIQ_SERVER = true`, `name`, and `type` fields to appear in the menu.

---

## REST API

The plugin communicates with the IIQ REST debug API:

| Operation | Endpoint |
|---|---|
| Search rule by name | `GET /rest/debug/Rule?query={name}` |
| Fetch rule XML | `GET /rest/debug/Rule/{id}` |
| Create rule | `POST /rest/debug/Rule` |
| Update rule | `POST /rest/debug/Rule/{id}` |

All requests use HTTP Basic Authentication.
