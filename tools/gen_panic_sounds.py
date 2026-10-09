"""Генерирует встроенные звуки (assets/farmmacro/sounds/panic/*.ogg): 4 сирены паники + done (конец макроса).
Запуск: python tools/gen_panic_sounds.py  (нужны numpy и ffmpeg с libvorbis)."""
import numpy as np, subprocess, os, wave
SR = 44100
OUT = os.path.join(os.path.dirname(__file__), '..', 'src/main/resources/assets/farmmacro/sounds/panic')

def tone(freq, dur, harm=(1, .45, .25, .12)):
    """freq: число или массив частот по сэмплам; гармоники дают «плотный» тембр."""
    n = int(SR * dur)
    f = np.full(n, freq, float) if np.isscalar(freq) else np.asarray(freq, float)
    ph = 2 * np.pi * np.cumsum(f) / SR
    return sum(a * np.sin(ph * (k + 1)) for k, a in enumerate(harm))

def env(x, a=.008, r=.03):
    n = len(x); e = np.ones(n)
    ai, ri = max(1, int(a * SR)), max(1, int(r * SR))
    e[:ai] = np.linspace(0, 1, ai); e[-ri:] = np.minimum(e[-ri:], np.linspace(1, 0, ri))
    return x * e

def gap(d): return np.zeros(int(SR * d))

def finish(x, name, drive=1.6):
    x = np.tanh(drive * x / np.max(np.abs(x)))          # мягкое насыщение — звучит громче и плотнее
    x = env(x, .005, .08) / np.max(np.abs(x)) * 0.89
    pcm = (x * 32767).astype('<i2')
    wav = os.path.join(OUT, name + '.wav')
    with wave.open(wav, 'wb') as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR); w.writeframes(pcm.tobytes())
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', wav, '-c:a', 'libvorbis', '-q:a', '5',
                    os.path.join(OUT, name + '.ogg')], check=True)
    os.remove(wav)

os.makedirs(OUT, exist_ok=True)

# 1. Сирена: два плавных подъёма-спада 650→1350 Гц с лёгким вибрато
t = np.arange(int(SR * 1.6)) / SR
sweep = 650 + 700 * (0.5 - 0.5 * np.cos(2 * np.pi * t / 0.8))
sweep *= 1 + 0.006 * np.sin(2 * np.pi * 7 * t)
finish(tone(sweep, 1.6), 'siren')

# 2. Двухтональная тревога «ти-та» (как у спецслужб): 960 / 720 Гц
seq = []
for i in range(6):
    seq.append(env(tone(960 if i % 2 == 0 else 720, .22, (1, .6, .35, .2, .1)), .004, .02))
finish(np.concatenate(seq), 'klaxon', drive=2.0)

# 3. Пульсирующий сигнал: 3 коротких писка + пауза, дважды
beeps = []
for _ in range(2):
    for _ in range(3):
        beeps += [env(tone(1150, .11, (1, .3, .15)), .003, .015), gap(.06)]
    beeps.append(gap(.22))
finish(np.concatenate(beeps), 'alarm', drive=1.4)

# 4. Цифровая тревога: быстрое чередование 1250/1650 Гц, две серии
warble = []
for _ in range(2):
    for i in range(16):
        warble.append(env(tone(1250 if i % 2 == 0 else 1650, .035, (1, .25)), .002, .004))
    warble.append(gap(.18))
finish(np.concatenate(warble), 'alert', drive=1.3)
# 5. Мягкий «дзинь» об окончании макроса (не тревога): C6-E6-G6 с затуханием
def bell(freq, dur):
    n = int(SR * dur); t = np.arange(n) / SR
    x = (np.sin(2*np.pi*freq*t) + .35*np.sin(2*np.pi*2*freq*t) + .12*np.sin(2*np.pi*3.01*freq*t))
    return x * np.exp(-t * 5.5)
notes = [bell(f, .55) for f in (1046.5, 1318.5, 1568.0)]
done = np.zeros(int(SR * 1.15))
for i, nt in enumerate(notes):
    o = int(SR * .14 * i); done[o:o+len(nt)] += nt
finish(done, 'done', drive=0.8)
print('ok')
