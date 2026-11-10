(() => {
    "use strict";
    document.addEventListener("DOMContentLoaded", () => {
        const splash = document.getElementById("status-splash");
        if (!splash || splash.classList.contains("show-image--false")) return;
        const loader = document.createElement("div");
        loader.id = "shroom-loader";
        const art = document.createElement("div");
        art.id = "shroom-loader-art";
        const subtitle = document.createElement("p");
        subtitle.id = "shroom-loader-subtitle";
        subtitle.textContent = "DEEP SPORE CONTAINMENT";
        splash.alt = "Shroom";
        splash.before(loader);
        loader.append(art, subtitle);
        art.append(splash);
    }, { once: true });

    const AudioContext = window.AudioContext || window.webkitAudioContext;
    if (!AudioContext) return;

    let context, gain, effectsGain, buffer, loading, source, encodedTrack, pendingEffect;
    let enabled = false, pageHidden = false, level = 0, effectsLevel = 0, offset = 0, startedAt = 0;
    const effectBuffers = new Map();
    const activeEffects = new Set();

    function foreground() {
        return !pageHidden && !document.hidden;
    }

    function wantsMusic() {
        return enabled && level > 0 && foreground();
    }

    function wantsEffects() {
        return effectsLevel > 0 && foreground();
    }

    function stopEffect(effect) {
        if (!activeEffects.delete(effect)) return;
        effect.stop();
        effect.disconnect();
    }

    function playEffect(key, pitch) {
        const cue = effectBuffers.get(key);
        if (!cue || !wantsEffects()) return;
        if (context.state !== "running") {
            pendingEffect = { key, pitch, at: performance.now() };
            unlock();
            return;
        }
        if (activeEffects.size >= 8) stopEffect(activeEffects.values().next().value);
        const effect = context.createBufferSource();
        effect.buffer = cue;
        effect.playbackRate.value = pitch;
        effect.connect(effectsGain);
        effect.onended = () => {
            if (activeEffects.delete(effect)) effect.disconnect();
        };
        activeEffects.add(effect);
        effect.start();
    }

    function sync() {
        if (!context) return;
        if (!wantsEffects()) {
            pendingEffect = undefined;
            for (const effect of activeEffects) stopEffect(effect);
        } else {
            effectsGain.gain.setTargetAtTime(effectsLevel * 0.48, context.currentTime, 0.02);
            if (pendingEffect && context.state === "running") {
                const cue = pendingEffect;
                pendingEffect = undefined;
                if (performance.now() - cue.at < 250) playEffect(cue.key, cue.pitch);
            }
        }
        if (!wantsMusic() || context.state !== "running") {
            if (source) {
                offset = (offset + context.currentTime - startedAt) % buffer.duration;
                source.stop();
                source.disconnect();
                source = undefined;
            }
            return;
        }
        gain.gain.setTargetAtTime(level, context.currentTime, 0.045);
        if (source || !buffer) return;
        source = context.createBufferSource();
        source.buffer = buffer;
        source.loop = true;
        source.connect(gain);
        startedAt = context.currentTime;
        source.start(0, offset);
    }

    function unlock() {
        if (!wantsMusic() && !wantsEffects()) return;
        if (!context) {
            context = new AudioContext({ latencyHint: "interactive" });
            const limiter = context.createDynamicsCompressor();
            limiter.threshold.value = -12;
            limiter.ratio.value = 12;
            limiter.connect(context.destination);
            gain = context.createGain();
            gain.gain.value = level;
            gain.connect(limiter);
            effectsGain = context.createGain();
            effectsGain.gain.value = effectsLevel * 0.48;
            effectsGain.connect(limiter);
            context.addEventListener("statechange", sync);
        }
        if (wantsMusic() && encodedTrack && !buffer && !loading) {
            loading = Promise.resolve().then(() => {
                const bytes = atob(encodedTrack);
                const track = new Uint8Array(bytes.length);
                for (let i = 0; i < bytes.length; i++) track[i] = bytes.charCodeAt(i);
                return context.decodeAudioData(track.buffer);
            }).then(decoded => {
                buffer = decoded;
                encodedTrack = undefined;
                sync();
            }).catch(error => console.warn("Shroom soundtrack:", error))
                .finally(() => { loading = undefined; });
        }
        if (context.state !== "running") context.resume().then(sync).catch(() => {});
        sync();
    }

    window.shroomScore = {
        provideTrack(base64) {
            if (!buffer) encodedTrack = base64;
        },
        provideEffect(key, base64, rate, channels) {
            if (effectBuffers.has(key)) return;
            if (!context) unlock();
            if (!context) return;
            const bytes = atob(base64);
            const frames = Math.floor(bytes.length / (2 * channels));
            if (frames === 0) return;
            const cue = context.createBuffer(channels, frames, rate);
            for (let channel = 0; channel < channels; channel++) {
                const output = cue.getChannelData(channel);
                for (let frame = 0; frame < frames; frame++) {
                    const index = 2 * (frame * channels + channel);
                    const value = bytes.charCodeAt(index) | (bytes.charCodeAt(index + 1) << 8);
                    output[frame] = (value << 16 >> 16) / 32768;
                }
            }
            effectBuffers.set(key, cue);
        },
        playEffect,
        configure(playing, volume, effectsVolume) {
            const wasWanted = wantsMusic();
            enabled = playing;
            level = volume;
            effectsLevel = effectsVolume;
            if ((wantsMusic() || wantsEffects()) && (!context || context.state !== "running" || (!wasWanted && wantsMusic()))) unlock();
            else sync();
        }
    };
    for (const event of ["pointerdown", "pointerup", "touchend", "keydown"]) {
        window.addEventListener(event, unlock, { capture: true, passive: true });
    }
    document.addEventListener("visibilitychange", () => foreground() ? unlock() : sync());
    window.addEventListener("pagehide", () => { pageHidden = true; sync(); });
    window.addEventListener("pageshow", () => { pageHidden = false; unlock(); });
})();
