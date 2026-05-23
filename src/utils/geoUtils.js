import { Geolocation } from '@capacitor/geolocation';

export const getFormattedLocation = async () => {
    try {
        const checkPermission = await Geolocation.checkPermissions();
        if (checkPermission.location !== 'granted') {
            const request = await Geolocation.requestPermissions();
            if (request.location !== 'granted') {
                return {
                    text: "Location Access Denied",
                    lat: null,
                    lng: null,
                    speed: 0
                };
            }
        }

        const position = await Geolocation.getCurrentPosition({
            enableHighAccuracy: true,
            timeout: 10000,
            maximumAge: 3000
        });

        const { latitude, longitude, speed, accuracy } = position.coords;
        const text = `Lat: ${latitude.toFixed(5)} | Long: ${longitude.toFixed(5)}`;

        // Speed is in m/s, convert to km/h
        const speedKmph = speed ? Math.round(speed * 3.6) : 0;

        return {
            text,
            lat: latitude,
            lng: longitude,
            speed: speedKmph,
            accuracy: Math.round(accuracy)
        };

    } catch (error) {
        console.warn("GPS Error", error);
        return {
            text: "Location Unavailable",
            lat: null,
            lng: null,
            speed: 0
        };
    }
};

export const generateMapsLink = (lat, lng) => {
    if (!lat || !lng) return "";
    return `https://maps.google.com/?q=${lat},${lng}`;
};
