# 📱 Personal Mobile Cloud Storage

A lightweight **personal cloud storage system** that runs directly on an **Android mobile phone (up to Android 11)**. This project turns your phone into a private cloud server, allowing you to store, manage, and access files securely—without relying on third‑party cloud providers.

---

## 🚀 Features

* 📦 Self‑hosted cloud storage on your own mobile phone
* 🔐 Full control over your data (no third‑party cloud dependency)
* 🌍 Secure remote access from anywhere
* ⚡ Lightweight and mobile‑friendly setup
* 🆓 Uses open‑source tools only

---

## 📱 Platform Support

* **Android version:** Up to Android 11
* **Device type:** Android smartphone
* **Root required:** ❌ No

---

## 🛠️ Tech Stack

* **Android App (Local Server)** – The app itself creates and runs the local server on the mobile device
* **Termux** (from F-Droid) – Used **only for tunneling purposes**, not for hosting the server
* **zrok** – Secure tunneling to expose the app’s local server to the internet

---

## 🔧 How It Works

1. The **Android app** creates and runs a local server directly on the mobile device.
2. Files are stored securely within the phone’s internal storage or SD card.
3. **Termux** is used **only to run zrok** for tunneling purposes.
4. **zrok** securely exposes the app’s local server to the internet.
5. The private cloud can then be accessed from anywhere using a secure link.

---

## 📌 Important Note

> 🔔 **Remote Access Architecture**
>
> The local server is **entirely handled by the Android app itself**. **Termux (installed via F-Droid)** is used **only for running zrok**, which securely tunnels the app’s local server to the public internet. This design keeps the app independent while still enabling global access to your private cloud storage.

---

## 🔐 Security Considerations

* Data remains stored only on your personal device
* zrok provides encrypted tunneling
* Access links can be rotated or revoked
* Optional authentication can be added at the application level

---

## 🎯 Use Cases

* Personal cloud storage alternative
* Backup server for important files
* File sharing between personal devices
* Learning project for mobile self‑hosting

---

## 📚 Future Enhancements

* Web‑based UI for file management
* User authentication & access roles
* File upload/download progress tracking
* Android background service support

---

## 🧠 Why This Project?

This project is designed for users who want:

* Complete ownership of their data
* A low‑cost cloud solution
* A practical demonstration of mobile self‑hosting
* Privacy‑focused storage infrastructure

---

## 📄 License

This project is open‑source and intended for educational and personal use.

---

✨ *Your phone. Your cloud. Full control.*
