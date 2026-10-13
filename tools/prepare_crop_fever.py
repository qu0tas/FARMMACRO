#!/usr/bin/env python3
"""Rebuild PNG/OGG resources. Requires Pillow + numpy and ffmpeg/ffprobe on PATH."""
from pathlib import Path
from tempfile import TemporaryDirectory
from PIL import Image
import numpy as np
import json, math, subprocess
root = Path(__file__).resolve().parent.parent
assets = root / 'src/main/resources/assets/farmmacro'
source = root / 'tools/crop_fever'
folder = assets / 'textures/gui/crop_fever'
folder.mkdir(parents=True, exist_ok=True)

def run_json(args):
    return json.loads(subprocess.check_output(args))

def remove_green(image):
    rgb = np.asarray(image.convert('RGB'), dtype=np.float32)
    excess = rgb[:, :, 1] - np.maximum(rgb[:, :, 0], rgb[:, :, 2])
    alpha = np.clip((180 - excess) / 140, 0, 1)
    alpha[excess <= 40] = 1
    foreground = rgb.copy()
    foreground[:, :, 1] -= (1-alpha)*204
    foreground /= np.maximum(alpha[:, :, None], .001)
    foreground[:, :, 1] = np.where(alpha < .999,
        np.minimum(foreground[:, :, 1], np.maximum(foreground[:, :, 0], foreground[:, :, 2])+8),
        foreground[:, :, 1])
    rgba = np.concatenate([np.clip(foreground, 0, 255), np.round(alpha[:, :, None]*255)], axis=2).astype(np.uint8)
    rgba[alpha <= 0, :3] = 0
    return Image.fromarray(rgba)

for kind, columns in [('being', 10), ('end', 4)]:
    if kind == 'being':
        video = source / 'CROP_FEVER_being.mp4'
        info = run_json(['ffprobe', '-v', 'error', '-select_streams', 'v:0',
            '-show_entries', 'stream=duration', '-of', 'json', str(video)])
        duration = float(info['streams'][0]['duration'])
        probe = run_json(['ffprobe', '-v', 'error', '-select_streams', 'v:0',
            '-show_entries', 'frame=best_effort_timestamp_time', '-of', 'json', str(video)])
        pts = [float(f['best_effort_timestamp_time']) for f in probe['frames']]
        durations = [round(((pts[i+1] if i+1 < len(pts) else duration)-t)*1000)
                     for i,t in enumerate(pts)]
        with TemporaryDirectory() as temp:
            subprocess.run(['ffmpeg','-v','error','-y','-i',str(video),'-fps_mode','passthrough',
                            str(Path(temp)/'frame-%04d.png')], check=True)
            frames = [remove_green(Image.open(p)) for p in sorted(Path(temp).glob('frame-*.png'))]
        assert len(frames) == len(durations) and all(ms > 0 for ms in durations)
    else:
        gif = Image.open(source / 'CROP_FEVER_end.gif')
        frames, durations = [], []
        for i in range(gif.n_frames):
            gif.seek(i)
            durations.append(max(20,gif.info.get('duration',40)))
            frames.append(gif.convert('RGBA').copy())
    w,h = frames[0].size
    atlas = Image.new('RGBA',(w*columns,h*math.ceil(len(frames)/columns)))
    for i,frame in enumerate(frames):
        atlas.paste(frame,((i%columns)*w,(i//columns)*h))
    atlas.save(folder/f'{kind}.png',optimize=True)
    meta = dict(width=w,height=h,columns=columns,frameDurationsMs=durations)
    if kind == 'being': meta.update(source='CROP_FEVER_being.mp4',transparent=True)
    (folder/f'{kind}.json').write_text(json.dumps(meta))
    audio = assets/'sounds/crop_fever'
    audio.mkdir(parents=True,exist_ok=True)
    subprocess.run(['ffmpeg','-v','error','-y','-i',str(source/f'CROP_FEVER_{kind}.mp3'),
                    '-c:a','libvorbis','-q:a','5',str(audio/f'{kind}.ogg')],check=True)
    print(kind,len(frames),'frames,',atlas.size,'atlas,',sum(durations),'ms')
# Runtime uses generated constants: if another source video is used later, update its Animation
# dimensions/frameDurationsMs from being.json (or generate them during your build).
