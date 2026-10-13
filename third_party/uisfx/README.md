UISFX Minimal audio from https://github.com/romainsimon/uisfx/tree/9950fe66f993a6660dab9c2651dcbcd899ffd83b (CC0-1.0; see LICENSE-AUDIO).

Beam resource mapping:
- nav.ogg: packages/uisfx/sounds/minimal/hover.ogg
- activate.ogg: packages/uisfx/sounds/minimal/select.ogg
- popup.ogg: packages/uisfx/sounds/minimal/reaction.ogg

Audio files are copied unchanged. Beam master volume is 70%, multiplied by UISFX defaultVolume: hover 12%, select 20%, reaction 17% (effective gains 8.4%, 14%, 11.9%). Hover has a 60 ms cooldown. Repeated cues restart their existing stream instead of overlapping. SoundPool stops immediately; UISFX Web Audio uses a short fade when stopping.
