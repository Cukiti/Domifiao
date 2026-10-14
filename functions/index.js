const {onRequest} = require("firebase-functions/v2/https");
const {
  RecaptchaEnterpriseServiceClient,
} = require("@google-cloud/recaptcha-enterprise");
const admin = require("firebase-admin");
const nodemailer = require("nodemailer");

// Inicializar Firebase Admin si no está inicializado
if (admin.apps.length === 0) {
  admin.initializeApp();
}

// Configuración de reCAPTCHA
const projectID = "domifiao-9ac0f";
const siteKey = "6Lel2XIsAAAAAIF_ah78CjKmmyv__z2Ixcv3W_N1";

const client = new RecaptchaEnterpriseServiceClient();

// Configuración de Correo (Nodemailer)
const transporter = nodemailer.createTransport({
  service: "gmail",
  auth: {
    user: process.env.MAIL_USER,
    pass: process.env.MAIL_PASS,
  },
});

/**
 * Envía un código OTP de 6 dígitos al correo del usuario.
 */
exports.sendEmailOtp = onRequest(async (req, res) => {
  const email = req.body.email;
  if (!email) {
    return res.status(400).send("Falta el parámetro email");
  }

  try {
    const otp = Math.floor(100000 + Math.random() * 900000).toString();

    await admin.firestore().collection("email_otps").doc(email).set({
      otp: otp,
      createdAt: admin.firestore.Timestamp.now(),
      expiresAt: admin.firestore.Timestamp.fromMillis(Date.now() + 600000),
    });

    const mailOptions = {
      from: "\"Domifiao 🛵\" <domifiao@gmail.com>",
      to: email,
      subject: "Tu código de verificación de Domifiao",
      html: `
        <div style="font-family: Arial, sans-serif; text-align: center;">
          <h2>¡Bienvenido a Domifiao!</h2>
          <p>Tu código de seguridad es:</p>
          <h1 style="color: #4CAF50; letter-spacing: 5px;">${otp}</h1>
          <p>Este código vencerá en 10 minutes.</p>
        </div>
      `,
    };

    await transporter.sendMail(mailOptions);
    res.json({success: true, message: "Código enviado al correo"});
  } catch (error) {
    console.error("Error enviando OTP por correo:", error);
    res.status(500).json({success: false, message: error.message});
  }
});

/**
 * Verifica un código OTP enviado al correo.
 */
exports.verifyEmailOtp = onRequest(async (req, res) => {
  const {email, otp} = req.body;
  if (!email || !otp) {
    return res.status(400).send("Faltan parámetros email o otp");
  }

  try {
    const doc = await admin.firestore().collection("email_otps")
        .doc(email).get();
    if (!doc.exists) {
      return res.json({success: false, message: "Código no solicitado"});
    }

    const data = doc.data();
    const now = admin.firestore.Timestamp.now();

    if (now > data.expiresAt) {
      await doc.ref.delete();
      return res.json({success: false, message: "El código ha expirado"});
    }

    if (data.otp === otp) {
      await doc.ref.delete();

      let userRecord;
      try {
        userRecord = await admin.auth().getUserByEmail(email);
      } catch (e) {
        if (e.code === "auth/user-not-found") {
          userRecord = await admin.auth().createUser({email: email});
        } else {
          throw e;
        }
      }

      const customToken = await admin.auth().createCustomToken(userRecord.uid);

      res.json({
        success: true,
        message: "Código verificado",
        token: customToken,
      });
    } else {
      res.json({success: false, message: "Código incorrecto"});
    }
  } catch (error) {
    console.error("Error verificando OTP:", error);
    res.status(500).json({success: false, message: error.message});
  }
});

/**
 * Valida un token de reCAPTCHA Enterprise enviado desde Android.
 */
exports.validateRecaptcha = onRequest(async (req, res) => {
  if (req.method !== "POST") {
    return res.status(405).send("Method Not Allowed");
  }

  const {token, action} = req.body;
  if (!token || !action) {
    return res.status(400).send("Missing token or action");
  }

  try {
    const projectPath = client.projectPath(projectID);
    const request = {
      parent: projectPath,
      assessment: {
        event: {
          token: token,
          siteKey: siteKey,
          expectedAction: action,
        },
      },
    };

    const [response] = await client.createAssessment(request);

    if (!response.tokenProperties.valid) {
      const reason = response.tokenProperties.invalidReason;
      console.error(`Token inválido: ${reason}`);
      return res.status(403).json({
        success: false,
        reason: reason,
      });
    }

    if (response.tokenProperties.action !== action) {
      console.error("La acción no coincide");
      return res.status(403).json({success: false, reason: "Action mismatch"});
    }

    const score = response.riskAnalysis.score;
    console.log(`Evaluación completada. Score: ${score}`);

    res.json({
      success: score >= 0.0,
      score: score,
    });
  } catch (error) {
    console.error("Error validando reCAPTCHA:", error);
    res.status(500).send("Internal Server Error");
  }
});
