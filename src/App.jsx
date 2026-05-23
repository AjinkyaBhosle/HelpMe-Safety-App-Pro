import React, { useState, useEffect } from 'react';
import { Settings as SettingsIcon, AlertTriangle, Battery, Send, MoreVertical, Info, Shield, X, HelpCircle, History } from 'lucide-react';
import SettingsForm from './components/SettingsForm';
import SosPage from './pages/SosPage';
import SafetyPage from './pages/SafetyPage';
import PanicHistoryModal from './components/PanicHistoryModal';
import PermissionDisclosureModal from './components/PermissionDisclosureModal';
import UserProfileView from './components/UserProfileView';
import SettingsMenu from './components/SettingsMenu';
import SoundSelectorModal from './components/SoundSelectorModal';
import VoiceRecorderModal from './components/VoiceRecorderModal';
import CheckInModal from './components/CheckInModal';
import ProUpgradeModal from './components/ProUpgradeModal';
import { registerPlugin, Capacitor } from '@capacitor/core';
import { App as CapApp } from '@capacitor/app';
import { hapticService } from './services/HapticService';
import { SplashScreen } from '@capacitor/splash-screen';
import { StatusBar, Style } from '@capacitor/status-bar';

const SmsPlugin = registerPlugin('SmsPlugin');
const BillingPlugin = registerPlugin('BillingPlugin');

const AboutView = ({ onClose }) => (
  <div className="w-full max-w-md bg-surface p-6 rounded-2xl border border-zinc-800 shadow-xl animate-in fade-in zoom-in duration-300 max-h-[75vh] flex flex-col">
    <div className="flex justify-between items-center mb-6">
      <div className="flex items-center gap-2">
        <Shield className="w-5 h-5 text-green-400" />
        <h2 className="text-xl font-bold">About Us</h2>
      </div>
      <button onClick={() => { hapticService.light(); onClose(); }} className="p-1 hover:bg-zinc-800 rounded-full">
        <X size={20} className="text-zinc-400" />
      </button>
    </div>

    <div className="overflow-y-auto space-y-6 text-sm text-zinc-300 pr-2 pb-4">
      <section>
        <h3 className="font-semibold text-white mb-2">Our Aim</h3>
        <p>
          "Help Me!" works <b>offline first</b>. It provides a reliable tool for personal safety that works without Wi-Fi or Data. It allows you to quickly alert your trusted contacts when you are in need, using standard SMS and Calls.
        </p>
      </section>

      <section>
        <h3 className="font-semibold text-white mb-2">Key Features</h3>
        <ul className="list-disc pl-5 space-y-3 text-zinc-400 text-xs">
          <li>
            <b>Voice SOS:</b> Hands-free activation. Once enabled, the app continuously listens for the phrase "Help Me" in the background. If detected, it instantly triggers SOS alert even when device is locked. <i>Note: This keeps your microphone active and may increase battery consumption. If you swipe the app closed from recent tasks, Android may briefly pause the listener for 1-5 seconds before automatically restarting it.</i>
          </li>
          <li>
            <b>Core SOS:</b> Add up to 5 emergency contacts. When activated, the app sends SMS alerts to all 5 contacts and calls your primary contact.
          </li>
          <li>
            <b>Panic History:</b> Keeps a local log of your past alerts for your reference.
          </li>
          <li>
            <b>Flashlight:</b> Activates your phone's LED flash in a strobe pattern during SOS to attract visual attention.
          </li>
          <li>
            <b>Safety Sounds:</b> Plays loud sirens or whistles to attract attention. Uses your phone's maximum volume settings.
          </li>
          <li>
            <b>Voice Recorder:</b> Records audio using the device microphone. Recordings are saved locally in high-quality AAC format. Recording continues until you stop it or storage runs out.
          </li>
          <li>
            <b>Safety Cam:</b> Opens your camera to record video. automatically overlays your current GPS location and timestamp on the video file. Videos are saved to your device's gallery.
          </li>
          <li className="text-zinc-500 mt-2">
            <i>*Voice Recorder and Safety Cam require internet only while sharing the media.</i>
          </li>
          <li>
            <b>Scheduled Check-in:</b> A safety timer that you set. If you don't mark yourself as "Safe" before the timer expires, the app automatically triggers the SOS alert sequence (SMS & Call) to your contacts.
          </li>
        </ul>
      </section>

      <section className="p-3 bg-blue-900/20 border border-blue-800 rounded-lg text-blue-400/90 text-xs">
        <b>Private & Secure:</b> All your data (contacts, history, recordings) is stored locally on your phone.
      </section>
    </div>
  </div>
);

const InfoView = ({ onClose }) => (
  <div className="w-full max-w-md bg-surface p-6 rounded-2xl border border-zinc-800 shadow-xl animate-in fade-in zoom-in duration-300">
    <div className="flex justify-between items-center mb-6">
      <div className="flex items-center gap-2">
        <HelpCircle className="w-5 h-5 text-blue-400" />
        <h2 className="text-xl font-bold">Info & Help</h2>
      </div>
      <button onClick={() => { hapticService.light(); onClose(); }} className="p-1 hover:bg-zinc-800 rounded-full">
        <X size={20} className="text-zinc-400" />
      </button>
    </div>

    <div className="space-y-6 text-sm text-zinc-300">
      <section>
        <h3 className="font-semibold text-white mb-2">How to Use</h3>
        <ul className="list-disc pl-5 space-y-2 text-zinc-400 text-xs">
          <li><b>Setup:</b> Go to Settings and add up to 5 emergency contacts, separated by commas.</li>
          <li><b>Formatting:</b> Plain local digits (e.g., 5551234567) and full international codes (e.g., +15551234567) both work.</li>
          <li><b>Priority:</b> Add a trusted personal contact as your <b>first</b> number. Android blocks automated calls to official emergency numbers (911/100), so placing a personal number first ensures auto-dial works seamlessly.</li>
          <li><b>Panic:</b> Tap the big red button (or use Voice SOS) to trigger the alerts.</li>
        </ul>
      </section>

      <section>
        <h3 className="font-semibold text-white mb-2">Troubleshooting & Background Fixes</h3>
        <p className="text-xs text-zinc-500 mb-2">
          If alerts aren't sending or Voice SOS stops working after closing the app, ensure "Battery Optimization" is disabled.
        </p>
        <p className="text-xs text-yellow-500/90 font-medium">
          <b>OnePlus, Oppo, Vivo, & Xiaomi Users:</b><br/>
          Your phone's OS aggressively kills apps when you swipe them away. To ensure Voice SOS runs 24/7:<br/>
          1. Open your "Recent Apps" menu (swipe up and hold).<br/>
          2. Tap the 3 dots (or long-press) on the Help Me! app window.<br/>
          3. Tap <b>Lock</b> (a padlock icon will appear).<br/>
          This prevents the system from force-stopping the background safety listeners!
        </p>
      </section>
    </div>
  </div>
);

const PrivacyView = ({ onClose }) => (
  <div className="w-full max-w-md bg-surface p-6 rounded-2xl border border-zinc-800 shadow-xl animate-in fade-in zoom-in duration-300 max-h-[75vh] flex flex-col">
    <div className="flex justify-between items-center mb-4">
      <div className="flex items-center gap-2">
        <Shield className="w-5 h-5 text-zinc-400" />
        <h2 className="text-xl font-bold">Privacy Policy</h2>
      </div>
      <button onClick={() => { hapticService.light(); onClose(); }} className="p-1 hover:bg-zinc-800 rounded-full">
        <X size={20} className="text-zinc-400" />
      </button>
    </div>

    <div className="overflow-y-auto space-y-5 text-sm text-zinc-300 pr-2 pb-4">
      <section className="p-3 bg-green-900/20 border border-green-800 rounded-lg">
        <p className="text-green-300 text-xs">
          <b>Offline First:</b> Help Me! works without Wi-Fi or Mobile Data. The core features (SMS, Calls) rely on your cellular network, not the internet.
        </p>
      </section>

      <section>
        <h3 className="font-semibold text-white mb-2 text-base">What We Do NOT Collect</h3>
        <ul className="space-y-1.5 text-zinc-400 text-xs">
          <li>❌ No analytics or usage tracking</li>
          <li>❌ No personal information sent to servers</li>
          <li>❌ No background location tracking (except one-time during SOS)</li>
          <li>❌ No cloud backups or uploading of your recordings</li>
        </ul>
      </section>

      <section>
        <h3 className="font-semibold text-white mb-2 text-base">Data Stored Locally</h3>
        <p className="text-zinc-400 text-xs mb-2">The following data is stored <b>only on your device</b>:</p>
        <ul className="space-y-1.5 text-zinc-400 text-xs">
          <li><b>Emergency Contacts:</b> Phone numbers you added.</li>
          <li><b>User Details:</b> Your personal info (Name, Blood Group, etc).</li>
          <li><b>Panic History:</b> Logs of when you used SOS.</li>
          <li><b>Recordings & Videos:</b> Files created by Voice Recorder or Safety Cam.</li>
        </ul>
      </section>

      <section>
        <h3 className="font-semibold text-white mb-2 text-base">Permissions & Consent</h3>
        <p className="text-zinc-400 text-xs mb-2">We ask for permissions (Camera, Mic, Location) only to provide features involved in your safety.</p>
        <ul className="space-y-1.5 text-zinc-400 text-xs">
          <li><b>User Action Required:</b> Recording or sharing only happens when YOU tap a button.</li>
          <li><b>Voice SOS (Microphone):</b> If you enable the Voice SOS feature, the app listens in the background for the phrase "Help Me". This audio is processed instantaneously on your device using an offline AI model (Vosk). <b>Zero audio is recorded, saved, or sent to any server during this process.</b></li>
          <li><b>Your Control:</b> The app never records without your knowledge.</li>
        </ul>
      </section>

      <section className="mt-4 pt-4 border-t border-zinc-800">
        <h3 className="font-semibold text-white mb-2 text-xs uppercase tracking-wider">Internet Usage</h3>
        <p className="text-zinc-500 text-xs">
          Internet is only required if you choose to share a video or recording via WhatsApp/Telegram or other apps.
        </p>
      </section>
    </div>
  </div>
);

function App() {
  const [isInitializing, setIsInitializing] = useState(true); // New loading state
  const [loading, setLoading] = useState(false);
  const [view, setView] = useState('dashboard');
  const [showHistory, setShowHistory] = useState(false);
  const [showDisclosure, setShowDisclosure] = useState(false);
  const [showSettingsMenu, setShowSettingsMenu] = useState(false);
  const [navigationSource, setNavigationSource] = useState(null);
  const [settings, setSettings] = useState({
    email: localStorage.getItem('user_email') || '',
    phoneNumbers: '',
  });

  // Pro Billing State
  const [isPro, setIsPro] = useState(false);
  const [showProModal, setShowProModal] = useState(false);
  const [pendingProView, setPendingProView] = useState(null);
  const [isUpgrading, setIsUpgrading] = useState(false);
  const [upgradeError, setUpgradeError] = useState("");

  // Consolidated Initialization Logic
  useEffect(() => {
    const initApp = async () => {
      try {
        // 0. Setup Status Bar (Early to prevent layout shifts)
        if (Capacitor.getPlatform() !== 'web') {
          try {
            await StatusBar.setStyle({ style: Style.Dark });
            await StatusBar.setOverlaysWebView({ overlay: true });
          } catch (err) {
            console.warn("StatusBar setup failed", err);
          }
        }

        // 1. Initialize DB
        const { dbService } = await import('./services/DatabaseService');
        await dbService.initialize();

        // 2. Load Settings
        let settingsMissing = false;
        try {
          const data = await SmsPlugin.getSettings();
          setSettings(prev => ({
            ...prev,
            phoneNumbers: data.phoneNumbers || '',
          }));
          if (!data.phoneNumbers) {
            settingsMissing = true;
          }
        } catch (e) {
          console.error("Failed to load settings", e);
        }

        // 3. Check Permissions
        let needsDisclosure = false;
        try {
          const status = await SmsPlugin.checkPermissions();
          if (
            status.sms !== 'granted' ||
            status.call !== 'granted' ||
            status.camera !== 'granted' ||
            status.microphone !== 'granted'
          ) {
            needsDisclosure = true;
          } else {
            // Permissions granted, check GPS quietly
            verifyLocationServices();
          }
        } catch (e) {
          console.warn("Permission check failed:", e);
        }

        // 3.5 Check Pro Status
        try {
          if (Capacitor.getPlatform() === 'android') {
            const proRes = await BillingPlugin.getProStatus();
            setIsPro(proRes.isPro);
          }
        } catch (e) {
          console.warn("Failed to check Pro status", e);
        }

        // 4. Set Initial State
        if (needsDisclosure) {
          setShowDisclosure(true);
        }

        if (settingsMissing) {
          setView('settings');
        }

        // 5. Start Voice Listener if enabled
        if (localStorage.getItem('voiceActivation') === 'true') {
          try {
            await SmsPlugin.startVoiceListener();
          } catch (e) {
            console.warn("Failed to auto-start voice listener", e);
          }
        }

      } catch (err) {
        console.error("App initialization error:", err);
      } finally {
        // 5. Reveal App
        setIsInitializing(false);

        // Let React mount the DOM, then hide splash instantly
        // Android 12+ API handles the crossfade natively.
        setTimeout(async () => {
          await SplashScreen.hide();
        }, 200);
      }
    };

    initApp();
  }, []);

  useEffect(() => {
    const backButtonListener = CapApp.addListener('backButton', () => {
      if (showSettingsMenu) {
        setShowSettingsMenu(false);
      } else if (showProModal) {
        setShowProModal(false);
      } else if (showDisclosure) {
        // Cannot close disclosure with back button
      } else if (view !== 'dashboard') {
        setView('dashboard');
      } else {
        CapApp.exitApp();
      }
    });

    return () => {
      backButtonListener.then(listener => listener.remove());
    };
  }, [showSettingsMenu, showProModal, showDisclosure, view]);

  const verifyLocationServices = async () => {
    if (Capacitor.getPlatform() === 'android') {
      try {
        const locStatus = await SmsPlugin.isLocationServicesEnabled();
        if (!locStatus.enabled) {
          const enableGps = confirm(
            "🛰️ Location Services Disabled\n\n" +
            "For the app to find you in an emergency, your phone's Location (GPS) must be turned ON.\n\n" +
            "Tap OK to open Location Settings and enable it."
          );

          if (enableGps) {
            await SmsPlugin.openLocationSettings();
          }
        }
      } catch (gpsErr) {
        console.warn("Failed to check location services", gpsErr);
      }
    }
  };

  const handleDisclosureAccept = async () => {
    setShowDisclosure(false);
    try {
      await SmsPlugin.requestSmsPermission();

      if (Capacitor.getPlatform() === 'android') {
        try {
          const bgConfirm = confirm(
            "📍 Additional Permission Required\n\n" +
            "To enable features like 'Scheduled Check-in' to work when the app is closed, " +
            "you must select 'Allow all the time' for location in the next screen."
          );

          if (bgConfirm) {
            await SmsPlugin.requestPermissions({ permissions: ['background_location'] });
          }
        } catch (bgErr) {
          console.warn("Background location request failed", bgErr);
        }
        await verifyLocationServices();

        try {
          const overlayStatus = await SmsPlugin.checkOverlayPermission();
          if (!overlayStatus.granted) {
            const overlayConfirm = confirm(
              "📲 Background Call Permission Required\n\n" +
              "To ensure your phone automatically dials your emergency contact when locked, Android requires 'Display over other apps' permission.\n\n" +
              "Tap OK to open settings.\n\n" +
              "⚠️ IMPORTANT: If you see a long list of apps, use the search icon at the top to find 'Help Me!' and toggle it ON."
            );
            if (overlayConfirm) {
              await SmsPlugin.openOverlaySettings();
            }
          }
        } catch (overlayErr) {
          console.warn("Failed to check overlay permission", overlayErr);
        }
        
        try {
          const batStatus = await SmsPlugin.isIgnoringBatteryOptimizations();
          if (!batStatus.granted) {
            const batConfirm = confirm(
              "🔋 Battery Optimization\n\n" +
              "To ensure 'Voice SOS' and 'Scheduled Check-in' work reliably in the background, please disable battery optimization for 'Help Me!' in the next screen."
            );
            if (batConfirm) {
              await SmsPlugin.openBatteryOptimizationSettings();
            }
          }
        } catch (batErr) {
          console.warn("Failed to check battery optimization", batErr);
        }
      }
    } catch (e) {
      console.error("Failed to request permissions", e);
      if (e.message && e.message.includes("SMS")) {
        const userChoice = confirm(
          "⚠️ SMS Permission Blocked\n\n" +
          "Android has blocked automatic SMS permission for sideloaded apps as a security measure.\n\n" +
          "To enable SMS alerts:\n" +
          "1. Tap 'OK' to open App Settings\n" +
          "2. Go to 'Permissions\n" +
          "3. Enable 'SMS' permission manually\n\n" +
          "This is required for emergency alerts to work."
        );
        if (userChoice) {
          try {
            await SmsPlugin.openAppSettings();
          } catch (settingsErr) {
            console.error("Could not open settings", settingsErr);
          }
        }
      }
    }
  };

  const handleSaveSettings = async (data) => {
    setLoading(true);
    try {
      const email = data.email;
      const phoneNumbers = data.emergencyPhone || data.phoneNumbers;

      if (email) localStorage.setItem('user_email', email);

      try {
        await SmsPlugin.updateSettings({
          phoneNumbers: phoneNumbers
        });
        await SmsPlugin.requestSmsPermission();
      } catch (nativeErr) {
        console.warn("Native plugin update failed:", nativeErr);
      }

      setSettings(prev => ({
        ...prev,
        email,
        phoneNumbers
      }));
      handleCloseSubView();
    } catch (err) {
      alert('Failed to save settings: ' + err.message);
    } finally {
      setLoading(false);
    }
  };

  const handleUpgrade = async () => {
    setIsUpgrading(true);
    setUpgradeError("");
    try {
      const res = await BillingPlugin.purchasePro();
      if (res.success || res.isPro) {
        setIsPro(true);
        setShowProModal(false);
        if (pendingProView) {
          if (['flashlight', 'voice'].includes(pendingProView)) {
            // Re-open settings menu to let them try again
            setShowSettingsMenu(true);
          } else {
            setView(pendingProView);
          }
          setPendingProView(null);
        }
      }
    } catch (e) {
      console.error("Purchase failed", e);
      setUpgradeError(e.message || "Purchase was canceled or failed.");
    } finally {
      setIsUpgrading(false);
    }
  };

  const handleRestore = async () => {
    setIsUpgrading(true);
    setUpgradeError("");
    try {
      const res = await BillingPlugin.restorePurchases();
      if (res.isPro) {
        setIsPro(true);
        setShowProModal(false);
        alert("Pro purchases restored successfully!");
        if (pendingProView) {
          if (['flashlight', 'voice'].includes(pendingProView)) {
            setShowSettingsMenu(true);
          } else {
            setView(pendingProView);
          }
          setPendingProView(null);
        }
      } else {
        setUpgradeError("No Pro purchase found on this account.");
      }
    } catch (e) {
      console.error("Restore failed", e);
      setUpgradeError("Failed to restore purchases.");
    } finally {
      setIsUpgrading(false);
    }
  };

  const handleMenuNavigation = (action) => {
    setNavigationSource('settings-menu');
    setShowSettingsMenu(false);

    if (action === 'settings') {
      setView('settings');
    } else if (action === 'history') {
      setView('history');
    } else if (action === 'about') {
      setView('about');
    } else if (action === 'info') {
      setView('info');
    } else if (action === 'privacy') {
      setView('privacy');
    } else if (action === 'safety-cam') {
      setView('safety-cam');
    } else if (action === 'profile') {
      setView('profile');
    } else if (action === 'siren') {
      setView('siren');
    } else if (action === 'recorder') {
      setView('recorder');
    } else if (action === 'checkin') {
      setView('checkin');
    }
  };

  const handleCloseSubView = () => {
    if (navigationSource === 'settings-menu') {
      setShowSettingsMenu(true);

      // Delay switching to dashboard to allow Menu backdrop to fade in first
      // This prevents the "Flash of SOS Button" effect
      setTimeout(() => {
        setView('dashboard');
        // Optional: setNavigationSource(null) if you want to clear history, 
        // but keeping it until menu closes is also fine.
      }, 150);
    } else {
      setView('dashboard');
    }
  };


  if (isInitializing) {
    return <div className="min-h-screen bg-black" />;
  }

  return (
    <div 
      className="min-h-screen bg-black flex flex-col items-center relative overflow-hidden text-white font-sans"
      style={{
        paddingTop: 'env(safe-area-inset-top)',
        paddingBottom: 'env(safe-area-inset-bottom)',
        paddingLeft: 'env(safe-area-inset-left)',
        paddingRight: 'env(safe-area-inset-right)',
      }}
    >

      {/* Menu Header (Only visible if NOT in dashboard) */}
      {view !== 'dashboard' && (
        <header className="w-full max-w-md p-6 flex justify-between items-center z-50 mt-4 relative">
          <div className="flex items-center gap-3">
            <img src="/logo.png" alt="Logo" className="w-10 h-10 rounded-xl" />
            <h1 className="text-xl font-bold tracking-tight">Help Me!</h1>
          </div>
        </header>
      )}

      <main className="flex-1 w-full relative">
        {view === 'settings' ? (
          <div className="p-6 h-full flex items-center justify-center relative">
            <SettingsForm
              userSettings={{
                emergencyPhone: settings.phoneNumbers,
                isNative: true
              }}
              onSave={handleSaveSettings}
              onCancel={handleCloseSubView}
              loading={loading}
            />
          </div>
        ) : view === 'about' ? (
          <div className="p-6 h-full flex items-center justify-center">
            <AboutView onClose={handleCloseSubView} />
          </div>
        ) : view === 'info' ? (
          <div className="p-6 h-full flex items-center justify-center">
            <InfoView onClose={handleCloseSubView} />
          </div>
        ) : view === 'privacy' ? (
          <div className="p-6 h-full flex items-center justify-center">
            <PrivacyView onClose={handleCloseSubView} />
          </div>
        ) : view === 'profile' ? (
          <div className="p-6 h-full flex items-center justify-center">
            <UserProfileView onClose={handleCloseSubView} />
          </div>
        ) : view === 'history' ? (
          <div className="p-6 h-full flex items-center justify-center">
            <PanicHistoryModal isOpen={true} onClose={handleCloseSubView} />
          </div>
        ) : view === 'siren' ? (
          <div className="p-6 h-full flex items-center justify-center">
            <SoundSelectorModal isOpen={true} onClose={handleCloseSubView} />
          </div>
        ) : view === 'recorder' ? (
          <div className="p-6 h-full flex items-center justify-center">
            <VoiceRecorderModal isOpen={true} onClose={handleCloseSubView} />
          </div>
        ) : view === 'checkin' ? (
          <div className="p-6 h-full flex items-center justify-center">
            <CheckInModal isOpen={true} onClose={handleCloseSubView} />
          </div>
        ) : view === 'safety-cam' ? (
          <SafetyPage onBack={handleCloseSubView} />
        ) : (
          <SosPage
            onSettingsClick={() => setShowSettingsMenu(true)}
            settings={settings}
            isPro={isPro}
          />
        )}
      </main>



      {/* Google Play Compliance: Permission Disclosure */}
      <PermissionDisclosureModal
        isOpen={showDisclosure}
        onAccept={handleDisclosureAccept}
      />

      <ProUpgradeModal
        isOpen={showProModal}
        onClose={() => { setShowProModal(false); setPendingProView(null); setUpgradeError(""); }}
        onUpgrade={handleUpgrade}
        onRestore={handleRestore}
        isUpgrading={isUpgrading}
        error={upgradeError}
      />

      {/* Settings Menu */}
      <SettingsMenu
        isOpen={showSettingsMenu}
        isPro={isPro}
        onUpgradeRequest={(action) => {
          setNavigationSource('settings-menu');
          setShowSettingsMenu(false);
          setPendingProView(action);
          setShowProModal(true);
        }}
        onClose={() => {
          setShowSettingsMenu(false);
          // When closing menu, if we are still on a sub-view (like Configure),
          // reset to dashboard so next time we are clean.
          if (view !== 'dashboard' && view !== 'safety-cam') {
            setView('dashboard');
            setNavigationSource(null);
          }
        }}
        onNavigate={handleMenuNavigation}
      />
    </div>
  );
}
export default App;
