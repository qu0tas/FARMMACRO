#!/usr/bin/env python3
"""Opt-in destructive navigation arena in a SEPARATE local-only test world, never your saves."""
from pathlib import Path
import json, uuid
root = Path(__file__).resolve().parents[2]
server, client = root/'run-navserver', root/'run-navclient'
server.mkdir(exist_ok=True); client.mkdir(exist_ok=True)
(server/'eula.txt').write_text('eula=true\n')
(server/'server.properties').write_text('''online-mode=false
server-ip=127.0.0.1
server-port=25567
level-name=navworld
level-type=minecraft:flat
generator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains"}
spawn-protection=0
gamemode=creative
difficulty=peaceful
view-distance=5
simulation-distance=5
max-players=2
''')
import hashlib
raw=bytearray(hashlib.md5(b'OfflinePlayer:NavTester').digest());raw[6]=(raw[6]&15)|48;raw[8]=(raw[8]&63)|128
(server/'ops.json').write_text(json.dumps([dict(uuid=str(uuid.UUID(bytes=bytes(raw))),name='NavTester',level=4,bypassesPlayerLimit=True)]))
config=client/'config';config.mkdir(exist_ok=True)
p=config/'farmmacro.json';settings=json.loads(p.read_text()) if p.exists() else {}
settings.update(welcomeDone=True,lastSeenVersion='1.18.0-dev',pathNaturalLook=True,returnMaxDist=32,returnTurnSpeed=180,returnFly=False,pathDebug=True,detectPlayerNear=False,autoReconnect=False)
p.write_text(json.dumps(settings,ensure_ascii=False,indent=2))
print('Prepared isolated run-navserver/run-navclient. Port 25567 bound to 127.0.0.1 only. Requires local Minecraft installation/assets and Java 25.')
