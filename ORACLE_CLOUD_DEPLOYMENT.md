# Deploying the Vaadin Web App to Oracle Cloud Free Tier

This guide walks you through deploying the Trading Bot Vaadin app to an **Oracle Cloud Infrastructure (OCI) Always-Free** compute instance.

---

## Prerequisites

| Item | Details |
|------|---------|
| Oracle Cloud account | [Sign up for free](https://www.oracle.com/cloud/free/) (credit card required, but free tier is permanent) |
| SSH key pair | You'll generate or use an existing one |
| Git installed locally | To push code or SCP the JAR |
| Your Supabase credentials | `SUPABASE_URL`, `SUPABASE_ANON_KEY`, `SUPABASE_SERVICE_ROLE_KEY` |

---

## Step 1 — Create an Always-Free Compute Instance

1. Log in to the [OCI Console](https://cloud.oracle.com/).
2. Navigate to **Compute → Instances → Create Instance**.
3. Configure:
   - **Name**: `trading-bot`
   - **Image**: **Oracle Linux 8** (or Ubuntu 22.04 — both are free-tier eligible)
   - **Shape**: Select **VM.Standard.A1.Flex** (ARM, up to **4 OCPUs / 24 GB RAM free**) or **VM.Standard.E2.1.Micro** (AMD, 1 OCPU / 1 GB RAM free).

   > [!TIP]
   > The **A1.Flex** shape gives you far more resources for free. Recommended: **2 OCPUs + 12 GB RAM**.

4. **Networking**: Keep the defaults (new VCN + public subnet) or select an existing VCN.
5. **Add SSH Keys**: Upload your public key (`~/.ssh/id_rsa.pub`) or paste it in.
6. Click **Create**.
7. Wait for the instance to show **Running**, then note the **Public IP Address**.

---

## Step 2 — Open Port 8080 in the Security List

By default, OCI blocks all inbound traffic except SSH (22).

1. Go to **Networking → Virtual Cloud Networks** → click your VCN.
2. Click the **public subnet** → click the **Default Security List**.
3. Click **Add Ingress Rules**:
   - **Source CIDR**: `0.0.0.0/0`
   - **Destination Port Range**: `8080`
   - **Protocol**: TCP
   - **Description**: `Vaadin Web App`
4. Click **Add Ingress Rules**.

> [!IMPORTANT]
> You must ALSO open the port in the OS firewall (see Step 4 below). OCI Security List and OS firewall are independent layers.

---

## Step 3 — SSH into the Instance & Install Java 17

### Connect

```bash
ssh -i ~/.ssh/id_rsa opc@<YOUR_PUBLIC_IP>
# If Ubuntu image: ssh -i ~/.ssh/id_rsa ubuntu@<YOUR_PUBLIC_IP>
```

### Install Java 17 (Oracle Linux / RHEL)

```bash
sudo dnf install -y java-17-openjdk java-17-openjdk-devel
java -version
```

### Install Java 17 (Ubuntu)

```bash
sudo apt update
sudo apt install -y openjdk-17-jdk
java -version
```

### Install Maven (needed only if building on-server)

```bash
# Oracle Linux
sudo dnf install -y maven

# Ubuntu
sudo apt install -y maven
```

### Install Git

```bash
# Oracle Linux
sudo dnf install -y git

# Ubuntu
sudo apt install -y git
```

---

## Step 4 — Open Port 8080 in the OS Firewall

### Oracle Linux (firewalld)

```bash
sudo firewall-cmd --permanent --add-port=8080/tcp
sudo firewall-cmd --reload
sudo firewall-cmd --list-ports   # verify 8080 is listed
```

### Ubuntu (iptables)

```bash
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 8080 -j ACCEPT
sudo netfilter-persistent save
```

---

## Step 5 — Deploy the Application

You have two options: **build locally and upload the JAR**, or **build on the server**.

### Option A: Build Locally, Upload JAR (Recommended)

**On your local machine (Windows PowerShell):**

```powershell
# Build the production JAR with Vaadin production mode
cd c:\Projects\trading-bot
mvn clean package -Pproduction -DskipTests
```

> [!NOTE]
> Vaadin requires the `-Pproduction` profile to bundle frontend assets. If you don't have a `production` profile defined, add one — see the [Appendix](#appendix-adding-the-vaadin-production-profile) below.

```powershell
# Upload the JAR to the server
scp -i ~/.ssh/id_rsa target/trading-bot-1.0-SNAPSHOT.jar opc@<YOUR_PUBLIC_IP>:/home/opc/
```

### Option B: Clone & Build on Server

```bash
cd /home/opc
git clone https://github.com/phemasundar/trading-bot.git
cd trading-bot
mvn clean package -Pproduction -DskipTests
```

---

## Step 6 — Configure Environment Variables

Create a `.env` file on the server (never commit this):

```bash
cat > /home/opc/.env << 'EOF'
SUPABASE_URL=https://your-project-id.supabase.co
SUPABASE_ANON_KEY=your-anon-key-here
SUPABASE_SERVICE_ROLE_KEY=your-service-role-key-here
EOF

chmod 600 /home/opc/.env
```

---

## Step 7 — Create a Systemd Service (Run as a Daemon)

This ensures the app starts automatically on boot and restarts on failure.

```bash
sudo tee /etc/systemd/system/trading-bot.service > /dev/null << 'EOF'
[Unit]
Description=Trading Bot Vaadin Web App
After=network.target

[Service]
User=opc
WorkingDirectory=/home/opc
EnvironmentFile=/home/opc/.env
ExecStart=/usr/bin/java -jar /home/opc/trading-bot-1.0-SNAPSHOT.jar --spring.profiles.active=production
Restart=always
RestartSec=10
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
EOF
```

Enable and start:

```bash
sudo systemctl daemon-reload
sudo systemctl enable trading-bot
sudo systemctl start trading-bot
```

Check status:

```bash
sudo systemctl status trading-bot
sudo journalctl -u trading-bot -f   # tail logs
```

---

## Step 8 — Access the App

Open your browser:

```
http://<YOUR_PUBLIC_IP>:8080
```

You should see the Trading Bot Dashboard! 🎉

---

## Step 9 (Optional) — Set Up a Domain + HTTPS with Nginx

For a production-ready setup with a custom domain and SSL:

### Install Nginx

```bash
# Oracle Linux
sudo dnf install -y nginx

# Ubuntu
sudo apt install -y nginx
```

### Configure Reverse Proxy

```bash
sudo tee /etc/nginx/conf.d/trading-bot.conf > /dev/null << 'EOF'
server {
    listen 80;
    server_name your-domain.com;

    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # WebSocket support (required for Vaadin Push)
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }
}
EOF
```

### Open Port 80/443 and Start Nginx

```bash
# OCI Security List: add ingress rules for ports 80 and 443

# OS Firewall (Oracle Linux)
sudo firewall-cmd --permanent --add-service=http
sudo firewall-cmd --permanent --add-service=https
sudo firewall-cmd --reload

sudo systemctl enable nginx
sudo systemctl start nginx
```

### Install SSL with Let's Encrypt (Certbot)

```bash
# Oracle Linux
sudo dnf install -y certbot python3-certbot-nginx

# Ubuntu
sudo apt install -y certbot python3-certbot-nginx

sudo certbot --nginx -d your-domain.com
```

Certbot auto-renews via a systemd timer.

---

## Appendix: Adding the Vaadin Production Profile

If your `pom.xml` doesn't already have a `production` profile, add this inside `<project>`:

```xml
<profiles>
    <profile>
        <id>production</id>
        <dependencies>
            <dependency>
                <groupId>com.vaadin</groupId>
                <artifactId>vaadin</artifactId>
                <exclusions>
                    <exclusion>
                        <groupId>com.vaadin</groupId>
                        <artifactId>vaadin-dev</artifactId>
                    </exclusion>
                </exclusions>
            </dependency>
        </dependencies>
        <build>
            <plugins>
                <plugin>
                    <groupId>com.vaadin</groupId>
                    <artifactId>vaadin-maven-plugin</artifactId>
                    <version>${vaadin.version}</version>
                    <executions>
                        <execution>
                            <goals>
                                <goal>prepare-frontend</goal>
                                <goal>build-frontend</goal>
                            </goals>
                        </execution>
                    </executions>
                </plugin>
            </plugins>
        </build>
    </profile>
</profiles>
```

You also need to set `vaadin.productionMode=true` when running in production. The systemd service handles this via `--spring.profiles.active=production`. Create a `src/main/resources/application-production.properties`:

```properties
vaadin.productionMode=true
```

---

## Useful Commands Cheat Sheet

| Action | Command |
|--------|---------|
| Start the app | `sudo systemctl start trading-bot` |
| Stop the app | `sudo systemctl stop trading-bot` |
| Restart the app | `sudo systemctl restart trading-bot` |
| View live logs | `sudo journalctl -u trading-bot -f` |
| View last 100 log lines | `sudo journalctl -u trading-bot -n 100` |
| Check app status | `sudo systemctl status trading-bot` |
| Redeploy (new JAR) | Upload new JAR → `sudo systemctl restart trading-bot` |
| Check disk usage | `df -h` |
| Check memory | `free -h` |
| Check Java version | `java -version` |

---

## Troubleshooting

| Problem | Solution |
|---------|----------|
| Can't reach `http://<IP>:8080` | Check both OCI Security List AND OS firewall rules |
| App starts then crashes | Run `sudo journalctl -u trading-bot -n 200` and check for missing env vars |
| Out of memory on Micro instance | Add `-Xmx512m` to the `ExecStart` java command. Consider upgrading to A1.Flex |
| `mvn package` fails on server | Ensure Maven + Java 17 are installed. Check `java -version` and `mvn -version` |
| Vaadin shows "Development Mode" | Ensure you built with `-Pproduction` and `vaadin.productionMode=true` is set |
| WebSocket errors in browser console | Add the Nginx WebSocket proxy headers (see Step 9) |

---

## Security Recommendations

> [!CAUTION]
> - **Never** commit your `.env` file or credentials to Git.
> - Use the **Service Role Key** only on the backend; the Anon Key is for public/read-only access.
> - Restrict the Security List source CIDR to your IP if this is a personal tool, instead of `0.0.0.0/0`.
> - Keep the OS and Java updated: `sudo dnf update -y` or `sudo apt update && sudo apt upgrade -y`.
