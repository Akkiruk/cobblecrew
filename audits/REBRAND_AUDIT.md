# CobbleCrew Rebrand Audit — "cobbleworkers"/"accieo" → "cobblecrew"/"akkiruk"

**Generated: 2026-02-21**

All references to `cobbleworkers`, `Cobbleworkers`, `COBBLEWORKERS`, `accieo`, or `Accieo` in source files (excluding `build/`, `bin/`, `.gradle/` build artifacts which are regenerated).

---

## CATEGORY: MOD_ID

The mod ID `"cobbleworkers"` is used throughout as the Minecraft mod identifier.

| File | Line(s) | Reference |
|------|---------|-----------|
| `common/src/main/kotlin/accieo/cobbleworkers/Cobbleworkers.kt` | 18 | `const val MODID = "cobbleworkers"` |
| `common/src/main/kotlin/accieo/cobbleworkers/config/CobbleworkersConfig.kt` | 15 | `@Config(name = "cobbleworkers")` — AutoConfig file name (creates `cobbleworkers.json`) |
| `common/src/main/resources/cobbleworkers.mixins.json` | filename | Mixin config filename |
| `common/src/main/resources/cobbleworkers.mixins.json` | 3 | `"package": "accieo.cobbleworkers.mixin"` |
| `fabric/src/main/resources/fabric.mod.json` | 3 | `"id": "cobbleworkers"` |
| `fabric/src/main/resources/fabric.mod.json` | 16 | `"icon": "assets/cobbleworkers/icon.png"` |
| `fabric/src/main/resources/fabric.mod.json` | 39 | `"cobbleworkers.mixins.json"` |
| `neoforge/src/main/resources/META-INF/neoforge.mods.toml` | 7 | `modId = "cobbleworkers"` |
| `neoforge/src/main/resources/META-INF/neoforge.mods.toml` | 14 | `logoFile = "assets/cobbleworkers/icon.png"` |
| `neoforge/src/main/resources/META-INF/neoforge.mods.toml` | 16, 23 | `[[dependencies.cobbleworkers]]` (×2) |
| `neoforge/src/main/resources/META-INF/neoforge.mods.toml` | 31 | `config = "cobbleworkers.mixins.json"` |
| `neoforge/src/main/kotlin/accieo/cobbleworkers/neoforge/CobbleworkersNeoForge.kt` | 38 | `@Mod(Cobbleworkers.MODID)` |
| `neoforge/src/main/kotlin/accieo/cobbleworkers/neoforge/CobbleworkersNeoForge.kt` | 64 | `event.registrar(Cobbleworkers.MODID)` |
| `common/src/main/kotlin/accieo/cobbleworkers/network/JobSyncPayload.kt` | 29 | `Identifier.of(Cobbleworkers.MODID, "job_sync")` |

---

## CATEGORY: DISPLAY_NAME

User-visible display name "Cobbleworkers" (some already changed to "CobbleCrew").

| File | Line(s) | Reference |
|------|---------|-----------|
| `fabric/src/main/resources/fabric.mod.json` | 5 | `"name": "CobbleCrew"` ✅ already rebranded |
| `neoforge/src/main/resources/META-INF/neoforge.mods.toml` | 9 | `displayName = "CobbleCrew"` ✅ already rebranded |
| `common/src/main/resources/assets/cobbleworkers/lang/en_us.json` | 2 | `"text.autoconfig.cobbleworkers.title": "Cobbleworkers config"` — user-visible config title |
| `common/src/main/kotlin/accieo/cobbleworkers/commands/CobbleworkersCommand.kt` | 34 | `CommandManager.literal("cobbleworkers")` — `/cobbleworkers` command |
| `common/src/main/kotlin/accieo/cobbleworkers/commands/CobbleworkersCommand.kt` | 45 | `Text.literal("[Cobbleworkers] Generating diagnostic report...")` |
| `common/src/main/kotlin/accieo/cobbleworkers/commands/CobbleworkersCommand.kt` | 64 | `Text.literal("[Cobbleworkers] Diagnostic report uploaded: ")` |
| `common/src/main/kotlin/accieo/cobbleworkers/commands/CobbleworkersCommand.kt` | 68 | `Text.literal("[Cobbleworkers] Upload failed...")` |
| `common/src/main/kotlin/accieo/cobbleworkers/commands/CobbleworkersCommand.kt` | 75 | `LOGGER.error("[Cobbleworkers] Debug dump failed")` |
| `common/src/main/kotlin/accieo/cobbleworkers/commands/CobbleworkersCommand.kt` | 78 | `Text.literal("[Cobbleworkers] Debug dump failed...")` |
| `common/src/main/kotlin/accieo/cobbleworkers/commands/CobbleworkersCommand.kt` | 103 | `LOGGER.warn("[Cobbleworkers] mclo.gs returned status...")` |
| `common/src/main/kotlin/accieo/cobbleworkers/commands/CobbleworkersCommand.kt` | 108 | `LOGGER.warn("[Cobbleworkers] Failed to upload...")` |
| `common/src/main/kotlin/accieo/cobbleworkers/api/CobbleworkersApi.kt` | 15 | `"Public API for Cobbleworkers."` (comment) |
| `common/src/main/kotlin/accieo/cobbleworkers/api/CobbleworkersApi.kt` | 213 | `"=== Cobbleworkers Diagnostic Report ==="` |
| `deploy-exaroton.ps1` | 1 | `# Deploy Cobbleworkers to Exaroton` |
| `deploy-exaroton.ps1` | 22 | `Write-Host "Building Cobbleworkers..."` |

---

## CATEGORY: AUTHOR

Author references: "Accieo" / "accieo"

| File | Line(s) | Reference |
|------|---------|-----------|
| `fabric/src/main/resources/fabric.mod.json` | 8-9 | `"authors": ["Akkiruk", "Accieo"]` |
| `neoforge/src/main/resources/META-INF/neoforge.mods.toml` | 10 | `authors = "Akkiruk, Accieo"` |
| `README.md` | 11 | Attribution: `Originally forked from [Cobbleworkers](...) by Accieo.` |
| `README.md` | 62 | Attribution: `...by [Accieo](https://github.com/Accieo).` |
| ALL `.kt` and `.java` source files | line 2 | `Copyright (C) 2025 Accieo` — in every license header (40+ files) |

---

## CATEGORY: PACKAGE_NAME

Java/Kotlin package: `accieo.cobbleworkers` — appears in EVERY source file as `package` or `import`.

### Package declarations (one per source file):

| File | Line | Declaration |
|------|------|-------------|
| `common/.../Cobbleworkers.kt` | 10 | `package accieo.cobbleworkers` |
| `common/.../api/CobbleworkersApi.kt` | 9 | `package accieo.cobbleworkers.api` |
| `common/.../cache/CobbleworkersCacheManager.kt` | 9 | `package accieo.cobbleworkers.cache` |
| `common/.../cache/PastureCache.kt` | 9 | `package accieo.cobbleworkers.cache` |
| `common/.../commands/CobbleworkersCommand.kt` | 9 | `package accieo.cobbleworkers.commands` |
| `common/.../config/CobbleworkersConfig.kt` | 9 | `package accieo.cobbleworkers.config` |
| `common/.../config/CobbleworkersConfigHolder.kt` | 9 | `package accieo.cobbleworkers.config` |
| `common/.../config/CobbleworkersConfigInitializer.kt` | 9 | `package accieo.cobbleworkers.config` |
| `common/.../config/JobConfig.kt` | 9 | `package accieo.cobbleworkers.config` |
| `common/.../config/JobConfigManager.kt` | 9 | `package accieo.cobbleworkers.config` |
| `common/.../enums/BlockCategory.kt` | 9 | `package accieo.cobbleworkers.enums` |
| `common/.../enums/WorkerPriority.kt` | 9 | `package accieo.cobbleworkers.enums` |
| `common/.../integration/CobbleworkersIntegrationHandler.kt` | 9 | `package accieo.cobbleworkers.integration` |
| `common/.../integration/FarmersDelightBlocks.kt` | 9 | `package accieo.cobbleworkers.integration` |
| `common/.../interfaces/ModIntegrationHelper.kt` | 9 | `package accieo.cobbleworkers.interfaces` |
| `common/.../interfaces/Worker.kt` | 9 | `package accieo.cobbleworkers.interfaces` |
| `common/.../jobs/BaseDefender.kt` | 9 | `package accieo.cobbleworkers.jobs` |
| `common/.../jobs/BaseHarvester.kt` | 9 | `package accieo.cobbleworkers.jobs` |
| `common/.../jobs/BasePlacer.kt` | 9 | `package accieo.cobbleworkers.jobs` |
| `common/.../jobs/BaseProcessor.kt` | 9 | `package accieo.cobbleworkers.jobs` |
| `common/.../jobs/BaseProducer.kt` | 9 | `package accieo.cobbleworkers.jobs` |
| `common/.../jobs/BaseSupport.kt` | 9 | `package accieo.cobbleworkers.jobs` |
| `common/.../jobs/PokemonProfile.kt` | 9 | `package accieo.cobbleworkers.jobs` |
| `common/.../jobs/WorkerDispatcher.kt` | 9 | `package accieo.cobbleworkers.jobs` |
| `common/.../jobs/WorkerRegistry.kt` | 9 | `package accieo.cobbleworkers.jobs` |
| `common/.../jobs/dsl/DefenseJob.kt` | 9 | `package accieo.cobbleworkers.jobs.dsl` |
| `common/.../jobs/dsl/GatheringJob.kt` | 9 | `package accieo.cobbleworkers.jobs.dsl` |
| `common/.../jobs/dsl/PlacementJob.kt` | 9 | `package accieo.cobbleworkers.jobs.dsl` |
| `common/.../jobs/dsl/ProcessingJob.kt` | 9 | `package accieo.cobbleworkers.jobs.dsl` |
| `common/.../jobs/dsl/ProductionJob.kt` | 9 | `package accieo.cobbleworkers.jobs.dsl` |
| `common/.../jobs/dsl/SupportJob.kt` | 9 | `package accieo.cobbleworkers.jobs.dsl` |
| `common/.../jobs/registry/ComboJobs.kt` | 9 | `package accieo.cobbleworkers.jobs.registry` |
| `common/.../jobs/registry/DefenseJobs.kt` | 9 | `package accieo.cobbleworkers.jobs.registry` |
| `common/.../jobs/registry/EnvironmentalJobs.kt` | 9 | `package accieo.cobbleworkers.jobs.registry` |
| `common/.../jobs/registry/GatheringJobs.kt` | 9 | `package accieo.cobbleworkers.jobs.registry` |
| `common/.../jobs/registry/LogisticsJobs.kt` | 9 | `package accieo.cobbleworkers.jobs.registry` |
| `common/.../jobs/registry/PlacementJobs.kt` | 9 | `package accieo.cobbleworkers.jobs.registry` |
| `common/.../jobs/registry/ProcessingJobs.kt` | 9 | `package accieo.cobbleworkers.jobs.registry` |
| `common/.../jobs/registry/ProductionJobs.kt` | 9 | `package accieo.cobbleworkers.jobs.registry` |
| `common/.../jobs/registry/SupportJobs.kt` | 9 | `package accieo.cobbleworkers.jobs.registry` |
| `common/.../network/JobSyncPayload.kt` | 9 | `package accieo.cobbleworkers.network` |
| `common/.../network/JobSyncSerializer.kt` | 9 | `package accieo.cobbleworkers.network` |
| `common/.../utilities/BlockCategoryValidators.kt` | 9 | `package accieo.cobbleworkers.utilities` |
| `common/.../utilities/CobbleworkersCauldronUtils.kt` | 9 | `package accieo.cobbleworkers.utilities` |
| `common/.../utilities/CobbleworkersCropUtils.kt` | 9 | `package accieo.cobbleworkers.utilities` |
| `common/.../utilities/CobbleworkersInventoryUtils.kt` | 9 | `package accieo.cobbleworkers.utilities` |
| `common/.../utilities/CobbleworkersMoveUtils.kt` | 9 | `package accieo.cobbleworkers.utilities` |
| `common/.../utilities/CobbleworkersNavigationUtils.kt` | 9 | `package accieo.cobbleworkers.utilities` |
| `common/.../utilities/CobbleworkersTags.kt` | 9 | `package accieo.cobbleworkers.utilities` |
| `common/.../utilities/DeferredBlockScanner.kt` | 9 | `package accieo.cobbleworkers.utilities` |
| `common/.../utilities/WorkerVisualUtils.kt` | 9 | `package accieo.cobbleworkers.utilities` |
| `common/.../mixin/AbstractFurnaceBlockEntityAccessor.java` | 9 | `package accieo.cobbleworkers.mixin;` |
| `common/.../mixin/BrewingStandBlockEntityAccessor.java` | 9 | `package accieo.cobbleworkers.mixin;` |
| `common/.../mixin/FarmlandBlockMixin.java` | 9 | `package accieo.cobbleworkers.mixin;` |
| `common/.../mixin/PokemonPastureBlockEntityMixin.java` | 9 | `package accieo.cobbleworkers.mixin;` |
| `fabric/.../CobbleworkersFabric.kt` | 9 | `package accieo.cobbleworkers.fabric` |
| `fabric/.../client/CobbleworkersFabricClient.kt` | 9 | `package accieo.cobbleworkers.fabric.client` |
| `fabric/.../client/config/CobbleworkersModMenu.kt` | 9 | `package accieo.cobbleworkers.fabric.client.config` |
| `fabric/.../integration/FabricIntegrationHelper.kt` | 9 | `package accieo.cobbleworkers.fabric.integration` |
| `fabric/.../client/ExampleModFabricClient.java` | 1 | `package accieo.cobbleworkers.fabric.client;` |
| `neoforge/.../CobbleworkersNeoForge.kt` | 9 | `package accieo.cobbleworkers.neoforge` |
| `neoforge/.../client/config/CobbleworkersModListScreen.kt` | 9 | `package accieo.cobbleworkers.neoforge.client.config` |
| `neoforge/.../integration/NeoForgeIntegrationHelper.kt` | 9 | `package accieo.cobbleworkers.neoforge.integration` |
| `neoforge/.../ExampleModNeoForge.java` | 1 | `package accieo.cobbleworkers.neoforge;` |

### Import statements (every Kotlin/Java file has `import accieo.cobbleworkers.*`):
Dozens of imports across all files. Every cross-package reference uses `import accieo.cobbleworkers.xxx`.

---

## CATEGORY: CLASS_NAME

Class/object names containing "Cobbleworkers":

| File | Line | Class/Object |
|------|------|-------------|
| `Cobbleworkers.kt` | 17 | `object Cobbleworkers` |
| `CobbleworkersApi.kt` | 18 | `object CobbleworkersApi` |
| `CobbleworkersCommand.kt` | 30 | `object CobbleworkersCommand` |
| `CobbleworkersConfig.kt` | 16 | `class CobbleworkersConfig` |
| `CobbleworkersConfigHolder.kt` | 11 | `object CobbleworkersConfigHolder` |
| `CobbleworkersConfigInitializer.kt` | 14 | `object CobbleworkersConfigInitializer` |
| `CobbleworkersIntegrationHandler.kt` | 19 | `class CobbleworkersIntegrationHandler` |
| `CobbleworkersCacheManager.kt` | — | `object CobbleworkersCacheManager` |
| `CobbleworkersCauldronUtils.kt` | — | `object CobbleworkersCauldronUtils` |
| `CobbleworkersCropUtils.kt` | — | `object CobbleworkersCropUtils` |
| `CobbleworkersInventoryUtils.kt` | — | `object CobbleworkersInventoryUtils` |
| `CobbleworkersMoveUtils.kt` | — | `object CobbleworkersMoveUtils` |
| `CobbleworkersNavigationUtils.kt` | — | `object CobbleworkersNavigationUtils` |
| `CobbleworkersTags.kt` | — | `object CobbleworkersTags` |
| `CobbleworkersFabric.kt` | 28 | `object CobbleworkersFabric` |
| `CobbleworkersFabricClient.kt` | 13 | `object CobbleworkersFabricClient` |
| `CobbleworkersModMenu.kt` | 18 | `class CobbleworkersModMenu` |
| `CobbleworkersNeoForge.kt` | 40 | `object CobbleworkersNeoForge` |
| `CobbleworkersModListScreen.kt` | 18 | `object CobbleworkersModListScreen` |

---

## CATEGORY: RESOURCE_PATH

Assets/data directory paths using `cobbleworkers` as namespace:

| Path | Type |
|------|------|
| `common/src/main/resources/assets/cobbleworkers/` | Assets root |
| `common/src/main/resources/assets/cobbleworkers/icon.png` | Mod icon |
| `common/src/main/resources/assets/cobbleworkers/lang/en_us.json` | Language file |
| `common/src/main/resources/data/cobbleworkers/` | Data root |
| `common/src/main/resources/data/cobbleworkers/recipe/honey_bottle_from_honeycombs.json` | Recipe |
| `common/src/main/resources/data/cobbleworkers/loot_table/archeology_treasure.json` | Loot table |
| `common/src/main/resources/data/cobbleworkers/loot_table/dive_treasure.json` | Loot table |

---

## CATEGORY: CONFIG

Config file references and config path (`config/cobbleworkers/`):

| File | Line(s) | Reference |
|------|---------|-----------|
| `common/.../config/CobbleworkersConfig.kt` | 15 | `@Config(name = "cobbleworkers")` — creates `config/cobbleworkers.json` |
| `common/.../config/JobConfigManager.kt` | 27 | `Paths.get("config", "cobbleworkers")` — per-job JSON dir |
| `common/src/main/resources/assets/cobbleworkers/lang/en_us.json` | ALL 127 lines | Every key is `text.autoconfig.cobbleworkers.option.*` |

---

## CATEGORY: MIXIN

| File | Line(s) | Reference |
|------|---------|-----------|
| `common/src/main/resources/cobbleworkers.mixins.json` | filename | File is named `cobbleworkers.mixins.json` |
| `common/src/main/resources/cobbleworkers.mixins.json` | 3 | `"package": "accieo.cobbleworkers.mixin"` |
| `fabric/src/main/resources/fabric.mod.json` | 39 | `"cobbleworkers.mixins.json"` |
| `neoforge/src/main/resources/META-INF/neoforge.mods.toml` | 31 | `config = "cobbleworkers.mixins.json"` |
| All 4 mixin `.java` files | 9 | `package accieo.cobbleworkers.mixin;` |

---

## CATEGORY: BUILD_SCRIPT

| File | Line(s) | Reference |
|------|---------|-----------|
| `gradle.properties` | 9 | `maven_group=accieo.cobbleworkers` |
| `gradle.properties` | 10 | `archives_name=cobbleworkers` |
| `settings.gradle` | 10 | `rootProject.name = 'cobbleworkers'` |

---

## CATEGORY: DEPLOY

| File | Line(s) | Reference |
|------|---------|-----------|
| `deploy-exaroton.ps1` | 1 | `# Deploy Cobbleworkers to Exaroton` |
| `deploy-exaroton.ps1` | 22 | `Write-Host "Building Cobbleworkers..."` |
| `deploy-exaroton.ps1` | 38 | `Get-ChildItem -Filter "cobbleworkers-fabric-*.jar"` |
| `deploy-exaroton.ps1` | 89 | `$_.name -match "^cobbleworkers-fabric"` |

---

## CATEGORY: GITHUB_URL

| File | Line(s) | Reference |
|------|---------|-----------|
| `.git/config` | 9 | `url = https://github.com/Accieo/cobbleworkers.git` (upstream remote) |
| `.git/config` | 16 | `url = https://github.com/Akkiruk/cobbleworkers.git` (origin remote) |
| `fabric/src/main/resources/fabric.mod.json` | 12-13 | `https://github.com/Akkiruk/cobbleworkers/issues` and `/cobbleworkers` |
| `neoforge/src/main/resources/META-INF/neoforge.mods.toml` | 3 | `https://github.com/Akkiruk/cobbleworkers/issues` |
| `README.md` | 5 | `/assets/cobbleworkers/icon.png` |
| `README.md` | 11, 62 | `https://github.com/Accieo/cobbleworkers` (attribution links) |

---

## CATEGORY: LOGGER

| File | Line(s) | Reference |
|------|---------|-----------|
| `Cobbleworkers.kt` | 21 | `LogManager.getLogger(MODID)` — logger name is `"cobbleworkers"` |
| `Cobbleworkers.kt` | 24 | `LOGGER.info("Launching {}...", MODID)` |
| Multiple files | various | `Cobbleworkers.LOGGER.info/debug/warn/error(...)` — references `Cobbleworkers` object |

---

## CATEGORY: WORKFLOW

| File | Line(s) | Reference |
|------|---------|-----------|
| `.github/workflows/build.yml` | — | No explicit "cobbleworkers" strings, but artifact paths reference `neoforge/build/libs/` and `fabric/build/libs/` which will contain `cobbleworkers-*.jar` filenames (derived from `archives_name`) |

---

## CATEGORY: DIRECTORY_PATH

Source directories that must be RENAMED (not including build artifacts):

| Current Path | Type |
|-------------|------|
| `common/src/main/kotlin/accieo/` | Kotlin source root (→ `akkiruk/`) |
| `common/src/main/kotlin/accieo/cobbleworkers/` | Package dir (→ `cobblecrew/`) |
| `common/src/main/java/accieo/` | Java/mixin source root |
| `common/src/main/java/accieo/cobbleworkers/` | Mixin package dir |
| `common/src/main/resources/assets/cobbleworkers/` | Assets namespace dir |
| `common/src/main/resources/data/cobbleworkers/` | Data namespace dir |
| `fabric/src/main/kotlin/accieo/` | Fabric Kotlin source |
| `fabric/src/main/kotlin/accieo/cobbleworkers/` | Fabric package dir |
| `fabric/src/main/java/accieo/` | Fabric Java source |
| `fabric/src/main/java/accieo/cobbleworkers/` | Fabric Java package dir |
| `neoforge/src/main/kotlin/accieo/` | NeoForge Kotlin source |
| `neoforge/src/main/kotlin/accieo/cobbleworkers/` | NeoForge package dir |
| `neoforge/src/main/java/accieo/` | NeoForge Java source |
| `neoforge/src/main/java/accieo/cobbleworkers/` | NeoForge Java package dir |

Build artifacts (`bin/`, `build/`) also contain these paths but are regenerated on build — no manual rename needed.

---

## CATEGORY: ENTRYPOINT_REF

Fully-qualified class references in config files (must match package rename):

| File | Line | Reference |
|------|------|-----------|
| `fabric/src/main/resources/fabric.mod.json` | 21 | `accieo.cobbleworkers.fabric.CobbleworkersFabric` |
| `fabric/src/main/resources/fabric.mod.json` | 27 | `accieo.cobbleworkers.fabric.client.CobbleworkersFabricClient` |
| `fabric/src/main/resources/fabric.mod.json` | 33 | `accieo.cobbleworkers.fabric.client.config.CobbleworkersModMenu` |

---

## CATEGORY: OTHER (Documentation, changelogs, audit files)

These files contain `cobbleworkers`/`Cobbleworkers`/`Accieo` references in documentation context:

| File | Nature |
|------|--------|
| `CHANGELOG.txt` | Multiple historical references (lines 71, 74, 77, 80, 85-88, 101, 103-104, 107, 112, 149, 159-161) |
| `COMPLETE_JOB_LIST.md` | References to `config/cobbleworkers/`, `cobbleworkers.json` (lines 1, 3, 651, 653, 665, 712) |
| `audits/DESIGN_LOGIC_AUDIT.md` | Title, class name references |
| `audits/SERVER_PERFORMANCE_AUDIT.md` | Title, `CobbleworkersNavigationUtils` reference |
| `audits/COBBLEWORKERS_V2_MASTER_PLAN.md` | Title and content |
| `audits/COMPREHENSIVE_CODE_AUDIT.md` | References throughout |
| `audits/JOB_USEFULNESS_TIERLIST.md` | References |

---

## SUMMARY — Rename Mapping

| Old | New | Scope |
|-----|-----|-------|
| `cobbleworkers` (mod ID) | `cobblecrew` | MODID constant, all config keys, resource paths, mixin filename |
| `Cobbleworkers` (class) | `CobbleCrew` | Main object, display strings |
| `accieo` (package root) | `akkiruk` | All package declarations, directory paths |
| `accieo.cobbleworkers` (package) | `akkiruk.cobblecrew` | All imports, packages |
| `Accieo` (author/copyright) | `Akkiruk` | License headers, author fields |
| `CobbleworkersXyz` (class names) | `CobbleCrewXyz` | All 19+ classes with Cobbleworkers prefix |
| `cobbleworkers-fabric-*.jar` | `cobblecrew-fabric-*.jar` | Deploy script, archive name |
| `config/cobbleworkers/` | `config/cobblecrew/` | JobConfigManager path |
| `cobbleworkers.json` | `cobblecrew.json` | AutoConfig file |
| `cobbleworkers.mixins.json` | `cobblecrew.mixins.json` | Mixin config file |
| `assets/cobbleworkers/` | `assets/cobblecrew/` | Minecraft assets namespace |
| `data/cobbleworkers/` | `data/cobblecrew/` | Minecraft data namespace |
| `/cobbleworkers` (command) | `/cobblecrew` | In-game command |
| `https://github.com/Accieo/cobbleworkers` | Keep as-is (upstream attribution) | README only |
| `https://github.com/Akkiruk/cobbleworkers` | `https://github.com/Akkiruk/cobblecrew` (if repo renamed) | Origin remote, issue URLs |

### Files affected (source only, excluding build artifacts): **~65 source files + 14 directories to rename**
