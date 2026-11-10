(() => {
    const tracks = ["rail", "blackout", "warden", "storm"];
    const files = ["train-bed", "jump", "tether", "strike", "hit", "block", "impact", "switch", "warn", "pickup", "win",
        "gust", "groan", "sweep", "charge", "furnace"];
    const buffers = new Map();
    const effects = new Map();
    const fading = new Set();
    const pending = new Set();
    let context, master, scoreGain, effectGain, score, bed, bedFilter, bedGain;
    let environment = "cabin";
    let active = false, pageHidden = false, music = true, volume = 0.8, track = "rail";
    let offset = 0;

    function canPlay() {
        return active && !pageHidden && !document.hidden && context?.state === "running";
    }

    function syncPlayback() {
        if (!canPlay()) stopEffects();
        updateScore();
        updateBed();
    }

    function updateScore() {
        if (!context) return;
        master.gain.value = volume;
        scoreGain.gain.value = music ? 0.63 : 0;
        const now = context.currentTime;
        if (!canPlay()) {
            if (score) {
                offset = (score.offset + now - score.startTime) % score.source.buffer.duration;
                score.source.stop();
                score = undefined;
            }
            for (const source of fading) source.stop();
            return;
        }
        const buffer = buffers.get(track);
        if (!buffer || score?.name === track) return;
        const previous = score;
        const source = context.createBufferSource();
        const gain = context.createGain();
        source.buffer = buffer;
        source.loop = true;
        source.connect(gain);
        gain.connect(scoreGain);
        source.onended = () => {
            source.disconnect();
            gain.disconnect();
            fading.delete(source);
        };
        if (previous) {
            offset = (previous.offset + now - previous.startTime) % previous.source.buffer.duration;
            previous.gain.gain.setValueAtTime(1, now);
            previous.gain.gain.linearRampToValueAtTime(0, now + 0.18);
            fading.add(previous.source);
            previous.source.stop(now + 0.18);
        }
        gain.gain.setValueAtTime(0, now);
        gain.gain.linearRampToValueAtTime(1, now + 0.18);
        score = {name: track, source, gain, offset, startTime: now};
        source.start(now, offset % buffer.duration);
    }

    function load(name) {
        if (buffers.has(name) || pending.has(name)) return;
        pending.add(name);
        fetch(name + ".wav")
            .then(response => {
                if (!response.ok) throw new Error("Audio asset unavailable");
                return response.arrayBuffer();
            })
            .then(bytes => context.decodeAudioData(bytes))
            .then(buffer => {
                buffers.set(name, buffer);
                if (tracks.includes(name)) updateScore();
                if (name === "train-bed") updateBed();
            })
            .catch(error => console.warn("Last Light audio:", name, error))
            .finally(() => pending.delete(name));
    }

    function updateBed() {
        if (!context) return;
        const buffer = buffers.get("train-bed");
        if (!canPlay() || !buffer) {
            if (bed) {
                bed.stop();
                bed.disconnect();
                bed = undefined;
            }
            return;
        }
        const [level, cutoff] = {
            cabin: [0.12, 240], roof: [0.20, 780], storm: [0.25, 1100], station: [0.06, 320],
        }[environment];
        bedGain.gain.setTargetAtTime(level, context.currentTime, 0.13);
        bedFilter.frequency.setTargetAtTime(cutoff, context.currentTime, 0.13);
        if (bed) return;
        bed = context.createBufferSource();
        bed.buffer = buffer;
        bed.loop = true;
        bed.connect(bedFilter);
        bed.start();
    }

    function unlock() {
        if (!active || pageHidden || document.hidden) return;
        if (!context) {
            const AudioContext = window.AudioContext || window.webkitAudioContext;
            if (!AudioContext) return;
            context = new AudioContext({ latencyHint: "playback" });
            master = context.createGain();
            scoreGain = context.createGain();
            effectGain = context.createGain();
            scoreGain.connect(master);
            effectGain.connect(master);
            master.connect(context.destination);
            effectGain.gain.value = 0.5;
            bedFilter = context.createBiquadFilter();
            bedFilter.type = "lowpass";
            bedGain = context.createGain();
            bedFilter.connect(bedGain);
            bedGain.connect(master);
            context.addEventListener("statechange", syncPlayback);
            for (const name of files) load(name);
            load(track);
        }
        if (context.state !== "running") {
            context.resume().then(syncPlayback).catch(() => {});
        } else {
            syncPlayback();
        }
    }

    function stopEffects() {
        for (const source of effects.values()) source.stop();
        effects.clear();
    }

    window.lastLightAudio = {
        configure(playing, enabled, level, arrangement = "rail") {
            active = playing;
            track = tracks.includes(arrangement) ? arrangement : "rail";
            music = enabled;
            volume = level;
            if (active && context?.state !== "running") unlock();
            if (active && context) load(track);
            syncPlayback();
        },
        setEnvironment(name) {
            environment = name;
            updateBed();
        },
        play(name) {
            if (!canPlay() || !buffers.has(name)) return;
            effects.get(name)?.stop();
            const source = context.createBufferSource();
            source.buffer = buffers.get(name);
            source.connect(effectGain);
            effects.set(name, source);
            source.onended = () => {
                source.disconnect();
                if (effects.get(name) === source) effects.delete(name);
            };
            source.start();
        },
    };

    for (const event of ["pointerup", "touchend", "keydown"]) {
        window.addEventListener(event, unlock, { capture: true, passive: true });
    }
    function syncVisibility() {
        if (active && !pageHidden && !document.hidden && context?.state !== "running") unlock();
        syncPlayback();
    }
    document.addEventListener("visibilitychange", syncVisibility);
    window.addEventListener("pagehide", () => {
        pageHidden = true;
        syncPlayback();
    });
    window.addEventListener("pageshow", () => {
        pageHidden = false;
        syncVisibility();
    });
})();
