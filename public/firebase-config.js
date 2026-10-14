// Import the functions you need from the SDKs you need
import { initializeApp } from "firebase/app";
import { getAnalytics } from "firebase/analytics";
import { initializeAppCheck, ReCaptchaEnterpriseProvider } from "firebase/app-check";

// TODO: Add SDKs for Firebase products that you want to use
// https://firebase.google.com/docs/web/setup#available-libraries

// Your web app's Firebase configuration
// For Firebase JS SDK v7.20.0 and later, measurementId is optional
const firebaseConfig = {
  apiKey: "AIzaSyDldDNRUmTsiTJDIR6_Aklnr5BaonjWd3U",
  authDomain: "domifiao-9ac0f.firebaseapp.com",
  projectId: "domifiao-9ac0f",
  storageBucket: "domifiao-9ac0f.firebasestorage.app",
  messagingSenderId: "105524180344",
  appId: "1:105524180344:web:989114a41bc978e3e3448d",
  measurementId: "G-NGNBCW74DE"
};

// Initialize Firebase
const app = initializeApp(firebaseConfig);
const analytics = getAnalytics(app);

// Initialize App Check
// TODO: Reemplaza 'TU_CLAVE_DE_SITIO_RECAPTCHA' con la clave que generes en la consola de Google Cloud/Firebase.
const appCheck = initializeAppCheck(app, {
  provider: new ReCaptchaEnterpriseProvider('6LeumnIsAAAAADiUKTbrzFGpISkWp9nfvX_naGYY'),
  isTokenAutoRefreshEnabled: true 
});

export { app, analytics, appCheck };
