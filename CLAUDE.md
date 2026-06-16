# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

`admin-seguridad-swing` is a standalone Java 17 Swing desktop tool for administering the VegaSoft security model. It manages the `DB_Seguridad` schema on a local SQL Server instance (`VegaSoftDB`) via raw JDBC — no ORM, no Spring.

## Commands

```bash
mvn compile                        # Compile
mvn exec:java                      # Run the application
mvn package                        # Build JAR
```

There are no tests in this project.

## Architecture

**Entry point:** `App.java` — applies FlatLightLaf theme, opens `MainFrame`.

**`MainFrame`** is a `JFrame` with a `JTabbedPane` containing six panels. Each panel is a self-contained `JPanel` that opens its own JDBC connections via `Conexion.get()`.

**`Conexion.java`** — static factory returning a `Connection` to `localhost:VegaSoftDB` (SQL Server). Credentials are hardcoded.

**`Cypher.java`** — encrypts passwords with Triple-DES (DESede), using an MD5-derived key from a fixed secret. This matches the encryption expected by the VegaSoft backend.

### Panels and their DB tables

| Panel | Tables touched |
|---|---|
| `PanelUsuarios` | `Sec_User`, `Sec_RoleUser` (assign/remove roles, copy config between users) |
| `PanelRoles` | `Sec_Role`, `Sec_ResourceRole` (assign/remove resources to roles) |
| `PanelRecursos` | `Sec_Resource` (CRUD, filtered by application) |
| `PanelAplicaciones` | `Sec_Application` (CRUD) |
| `PanelRolPermiso` | `Sec_Role`, `Sec_RolePermiso` (assign permissions to roles) |
| `PanelPermisos` | `Sec_Permission`, `Sec_Application` (CRUD, grouped by app) |

### UI pattern

All panels follow the same layout: a left `JTable` (master list with search/filter) and a right detail area (role lists, resource lists, or form fields) split by `JSplitPane`. Filtering uses `TableRowSorter` + `RowFilter.regexFilter`. Dual-list assignment widgets (Assigned / Available) store full lists in `List<String>` and re-filter from them on each keystroke.

PKs are generated via `SELECT ISNULL(MAX(PkCol), 0) + 1` — not database sequences or identity columns.

### Resource hierarchy

`Sec_Resource` has a self-referencing `MainCode` FK: `NULL` = menu item (parent), non-null = sub-option. Both panels that display resources use blue background + bold font for menus and indentation for sub-options.
