# K2-013 data sources / notices

No Aether or SkyHanni source code/assets copied or adapted. SkyHanni ItemResolutionQuery was read only to verify the public NBT shape: ATTRIBUTE_SHARD, ExtraAttributes.attributes, one entry at level1. Algorithm here is independently written.

## Included factual data
- stat-prices.json: NPC base sell prices and item ID/names from https://api.hypixel.net/v2/resources/skyblock/items . Snapshot lastUpdated embedded; not a live NPC-price promise or gear market appraisal.
- farming-xp.json: cumulative Farming0–60 thresholds from https://api.hypixel.net/v2/resources/skyblock/skills . Snapshot lastUpdated embedded. Unlock cap remains player-defined.
- shard-prices.json: projected attribute-key / Bazaar-ID / display-name correspondence from https://github.com/NotEnoughUpdates/NotEnoughUpdates-REPO/blob/master/constants/attribute_shards.json . MIT, Copyright (c) 2020 Moulberry. Full required license included in src/main/resources/META-INF/licenses/NEU-REPO-MIT.txt and thus in the JAR. No prices or implementation code. Non-tradable/unknown attributes are not assigned a market ID.

## Optional live source
https://api.hypixel.net/v2/skyblock/bazaar . Fixed public read-only endpoint, no API key or account payload. sell_summary bid maximum is a per-item indicative instant-sale quote; finite order depth, quantities/tax/slippage/market changes mean this is NOT guaranteed proceeds. Exact receipts remain separate. Public schema: https://api.hypixel.net/ .

Hunting-box virtual contents are not inferred from catches or inventory. Only real ATTRIBUTE_SHARD items with one known level1 attribute are priced by matched Bazaar product. NPC mode has no invented shard price. No profile API keys requested. Pet auction value is unknown unless manually annotated for observed type/tier; no auction scrape.
