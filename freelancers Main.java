// Import dependencies
const express = require("express");
const mongoose = require("mongoose");
const bcrypt = require("bcryptjs");
const jwt = require("jsonwebtoken");
const http = require("http");
const { Server } = require("socket.io");
const stripe = require("stripe")("your_stripe_secret_key");
const path = require("path");
const dotenv = require("dotenv");

dotenv.config();
const app = express();
const server = http.createServer(app);
const io = new Server(server);

app.use(express.urlencoded({ extended: true }));
app.use(express.json());
app.set("view engine", "ejs");
app.use(express.static(path.join(__dirname, "public")));

// MongoDB Connection
mongoose.connect("your_mongodb_connection_string", {
    useNewUrlParser: true,
    useUnifiedTopology: true,
}).then(() => console.log("MongoDB Connected")).catch(err => console.log(err));

// User Schema
const UserSchema = new mongoose.Schema({
    name: String,
    email: String,
    password: String,
    skills: [String],
});
const User = mongoose.model("User", UserSchema);

// Authentication Middleware
const authenticate = async (req, res, next) => {
    if (!req.headers.authorization) return res.redirect("/");
    try {
        const decoded = jwt.verify(req.headers.authorization.split(" ")[1], "your_jwt_secret");
        req.user = await User.findById(decoded.id);
        next();
    } catch {
        res.redirect("/");
    }
};

// Home Page
app.get("/", (req, res) => {
    res.render("index");
});

// Register Route
app.post("/register", async (req, res) => {
    const hashedPassword = await bcrypt.hash(req.body.password, 10);
    const user = await User.create({ name: req.body.name, email: req.body.email, password: hashedPassword });
    res.redirect("/");
});

// Login Route
app.post("/login", async (req, res) => {
    const user = await User.findOne({ email: req.body.email });
    if (user && await bcrypt.compare(req.body.password, user.password)) {
        const token = jwt.sign({ id: user._id }, "your_jwt_secret", { expiresIn: "7d" });
        res.json({ token });
    } else {
        res.status(401).send("Invalid credentials");
    }
});

// Chat Page
app.get("/chat", authenticate, (req, res) => {
    res.render("chat", { user: req.user });
});

// Payment Page
app.get("/payment", authenticate, (req, res) => {
    res.render("payment", { user: req.user });
});

// Stripe Payment Route
app.post("/pay", async (req, res) => {
    const paymentIntent = await stripe.paymentIntents.create({
        amount: req.body.amount * 100,
        currency: "usd",
        payment_method_types: ["card"],
    });
    res.json({ clientSecret: paymentIntent.client_secret });
});

// Real-Time Chat
io.on("connection", (socket) => {
    console.log("User Connected:", socket.id);
    socket.on("sendMessage", (data) => io.emit("receiveMessage", data));
    socket.on("disconnect", () => console.log("User Disconnected:", socket.id));
});

// Start Server
server.listen(3000, () => console.log("Server running on port 3000"));

//create views/index.ejs(home page)

<!DOCTYPE html>
<html>
<head>
    <title>Freelancer Platform</title>
</head>
<body>
    <h2>Login</h2>
    <form action="/login" method="POST">
        <input type="email" name="email" placeholder="Email" required />
        <input type="password" name="password" placeholder="Password" required />
        <button type="submit">Login</button>
    </form>

    <h2>Register</h2>
    <form action="/register" method="POST">
        <input type="text" name="name" placeholder="Name" required />
        <input type="email" name="email" placeholder="Email" required />
        <input type="password" name="password" placeholder="Password" required />
        <button type="submit">Register</button>
    </form>

    <a href="/chat">Go to Chat</a>
    <a href="/payment">Make a Payment</a>
</body>
</html>

  //create views/chat.ejs(chat page)
  <!DOCTYPE html>
<html>
<head>
    <title>Chat</title>
    <script src="/socket.io/socket.io.js"></script>
</head>
<body>
    <h2>Chat Room</h2>
    <div id="messages"></div>
    <input type="text" id="messageInput" />
    <button onclick="sendMessage()">Send</button>

    <script>
        const socket = io();
        document.getElementById("messageInput").addEventListener("keypress", (e) => {
            if (e.key === "Enter") sendMessage();
        });

        function sendMessage() {
            let message = document.getElementById("messageInput").value;
            socket.emit("sendMessage", message);
            document.getElementById("messageInput").value = "";
        }

        socket.on("receiveMessage", (data) => {
            let div = document.createElement("div");
            div.innerText = data;
            document.getElementById("messages").appendChild(div);
        });
    </script>
</body>
</html>

//create views/payment.ejs(payment page)
      <!DOCTYPE html>
<html>
<head>
    <title>Payment</title>
    <script src="https://js.stripe.com/v3/"></script>
</head>
<body>
    <h2>Make a Payment</h2>
    <input type="number" id="amount" placeholder="Enter amount" />
    <button onclick="pay()">Pay</button>

    <script>
        const stripe = Stripe("your_stripe_publishable_key");

        async function pay() {
            let amount = document.getElementById("amount").value;
            let res = await fetch("/pay", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ amount }),
            });

            let data = await res.json();
            const { error } = await stripe.confirmCardPayment(data.clientSecret, {
                payment_method: { card: { number: "4242424242424242", exp_month: 12, exp_year: 2025, cvc: "123" } },
            });

            if (!error) alert("Payment Successful");
        }
    </script>
</body>
</html>
  
