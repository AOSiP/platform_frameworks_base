# Copyright 2016 The Pure Nexus Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

LOCAL_PATH := frameworks/base/data/sounds/

PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)material/alarms/Argon.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/alarms/Argon.ogg \
    $(LOCAL_PATH)material/alarms/Awaken.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/alarms/Awaken.ogg \
    $(LOCAL_PATH)material/alarms/Bounce.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/alarms/Bounce.ogg \
    $(LOCAL_PATH)material/alarms/Carbon.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/alarms/Carbon.ogg \
    $(LOCAL_PATH)material/alarms/Drip.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/alarms/Drip.ogg \
    $(LOCAL_PATH)material/alarms/Gallop.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/alarms/Gallop.ogg \
    $(LOCAL_PATH)material/alarms/Helium.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/alarms/Helium.ogg \
    $(LOCAL_PATH)material/alarms/Krypton.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/alarms/Krypton.ogg \
    $(LOCAL_PATH)material/alarms/Neon.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/alarms/Neon.ogg \
    $(LOCAL_PATH)material/alarms/Nudge.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/alarms/Nudge.ogg \
    $(LOCAL_PATH)material/alarms/Orbit.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/alarms/Orbit.ogg \
    $(LOCAL_PATH)material/alarms/Osmium.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/alarms/Osmium.ogg \
    $(LOCAL_PATH)material/alarms/Oxygen.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/alarms/Oxygen.ogg \
    $(LOCAL_PATH)material/alarms/Platinum.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/alarms/Platinum.ogg \
    $(LOCAL_PATH)material/alarms/Rise.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/alarms/Rise.ogg \
    $(LOCAL_PATH)material/alarms/Sway.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/alarms/Sway.ogg \
    $(LOCAL_PATH)material/alarms/Timer.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/alarms/Timer.ogg \
    $(LOCAL_PATH)material/alarms/Wag.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/alarms/Wag.ogg \
    $(LOCAL_PATH)material/effects/audio_end.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ui/audio_end.ogg \
    $(LOCAL_PATH)material/effects/audio_initiate.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ui/audio_initiate.ogg \
    $(LOCAL_PATH)material/effects/camera_click.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ui/camera_click.ogg \
    $(LOCAL_PATH)material/effects/camera_focus.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ui/camera_focus.ogg \
    $(LOCAL_PATH)material/effects/Dock.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ui/Dock.ogg \
    $(LOCAL_PATH)material/effects/Effect_Tick.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ui/Effect_Tick.ogg \
    $(LOCAL_PATH)material/effects/KeypressDelete.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ui/KeypressDelete.ogg \
    $(LOCAL_PATH)material/effects/KeypressInvalid.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ui/KeypressInvalid.ogg \
    $(LOCAL_PATH)material/effects/KeypressReturn.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ui/KeypressReturn.ogg \
    $(LOCAL_PATH)material/effects/KeypressSpacebar.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ui/KeypressSpacebar.ogg \
    $(LOCAL_PATH)material/effects/KeypressStandard.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ui/KeypressStandard.ogg \
    $(LOCAL_PATH)material/effects/Lock.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ui/Lock.ogg \
    $(LOCAL_PATH)material/effects/LowBattery.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ui/LowBattery.ogg \
    $(LOCAL_PATH)material/effects/NFCFailure.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ui/NFCFailure.ogg \
    $(LOCAL_PATH)material/effects/NFCInitiated.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ui/NFCInitiated.ogg \
    $(LOCAL_PATH)material/effects/NFCSuccess.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ui/NFCSuccess.ogg \
    $(LOCAL_PATH)material/effects/NFCTransferComplete.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ui/NFCTransferComplete.ogg \
    $(LOCAL_PATH)material/effects/NFCTransferInitiated.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ui/NFCTransferInitiated.ogg \
    $(LOCAL_PATH)material/effects/Trusted.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ui/Trusted.ogg \
    $(LOCAL_PATH)material/effects/Undock.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ui/Undock.ogg \
    $(LOCAL_PATH)material/effects/Unlock.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ui/Unlock.ogg \
    $(LOCAL_PATH)material/effects/VideoRecord.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ui/VideoRecord.ogg \
    $(LOCAL_PATH)material/effects/VideoStop.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ui/VideoStop.ogg \
    $(LOCAL_PATH)material/effects/WirelessChargingStarted.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ui/WirelessChargingStarted.ogg \
    $(LOCAL_PATH)material/notifications/Ariel.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/notifications/Ariel.ogg \
    $(LOCAL_PATH)material/notifications/Carme.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/notifications/Carme.ogg \
    $(LOCAL_PATH)material/notifications/Ceres.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/notifications/Ceres.ogg \
    $(LOCAL_PATH)material/notifications/Elara.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/notifications/Elara.ogg \
    $(LOCAL_PATH)material/notifications/Europa.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/notifications/Europa.ogg \
    $(LOCAL_PATH)material/notifications/Iapetus.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/notifications/Iapetus.ogg \
    $(LOCAL_PATH)material/notifications/Io.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/notifications/Io.ogg \
    $(LOCAL_PATH)material/notifications/Rhea.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/notifications/Rhea.ogg \
    $(LOCAL_PATH)material/notifications/Salacia.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/notifications/Salacia.ogg \
    $(LOCAL_PATH)material/notifications/Tethys.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/notifications/Tethys.ogg \
    $(LOCAL_PATH)material/notifications/Titan.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/notifications/Titan.ogg \
    $(LOCAL_PATH)material/ringtones/Atria.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ringtones/Atria.ogg \
    $(LOCAL_PATH)material/ringtones/Callisto.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ringtones/Callisto.ogg \
    $(LOCAL_PATH)material/ringtones/Dione.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ringtones/Dione.ogg \
    $(LOCAL_PATH)material/ringtones/Ganymede.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ringtones/Ganymede.ogg \
    $(LOCAL_PATH)material/ringtones/Luna.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ringtones/Luna.ogg \
    $(LOCAL_PATH)material/ringtones/Oberon.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ringtones/Oberon.ogg \
    $(LOCAL_PATH)material/ringtones/Phobos.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ringtones/Phobos.ogg \
    $(LOCAL_PATH)material/ringtones/Pyxis.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ringtones/Pyxis.ogg \
    $(LOCAL_PATH)material/ringtones/Sedna.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ringtones/Sedna.ogg \
    $(LOCAL_PATH)material/ringtones/Titania.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ringtones/Titania.ogg \
    $(LOCAL_PATH)material/ringtones/Triton.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ringtones/Triton.ogg \
    $(LOCAL_PATH)material/ringtones/Umbriel.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ringtones/Umbriel.ogg

