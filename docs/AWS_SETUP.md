# Running the server on AWS (free plan)

New AWS accounts can use the **Free plan**: credits instead of a bill, for up to 6 months. When the 6 months end
(or the credits run out) AWS closes the account unless you switch to the Paid plan; the data is kept for 90 days.
Plan the move before then (see "Before month 6" below). Everything here is done in a web browser.

Server used: **t4g.small** (2 vCPU, 2 GB RAM, Arm) in **Mumbai**, 20 GB disk, a fixed public IP. This is on the
Free plan's list of instance types; it uses roughly $15–20 of credit a month.

## 1. Create the AWS account
1. Go to https://aws.amazon.com → **Create an AWS account**. Choose the **Free plan** when asked.
2. AWS asks for a phone number and a card to verify you. On the Free plan you are not charged.

## 2. Create the server
1. Sign in to the AWS console. Top right, choose the region **Asia Pacific (Mumbai) ap-south-1**.
2. Search for **EC2** → **Launch instance**.
3. Fill in:
   - **Name**: `stock-list-server`
   - **Application and OS image**: **Ubuntu**, *Ubuntu Server 24.04 LTS*, architecture **64-bit (Arm)**
   - **Instance type**: **t4g.small** (if it is not offered, take **t3.small** and the 64-bit (x86) Ubuntu image)
   - **Key pair**: *Proceed without a key pair* (GitHub uses its own key; you can still open a browser terminal)
   - **Network settings** → tick **Allow SSH traffic from Anywhere**, **Allow HTTPS traffic from the internet**,
     **Allow HTTP traffic from the internet**
   - **Configure storage**: **20** GiB, **gp3**
   - **Advanced details** → **User data**: paste the whole of
     [`deploy/server-setup.sh`](../deploy/server-setup.sh) (open it on GitHub → **Raw** → select all → copy)
4. **Launch instance**. Setup runs by itself for about 5 minutes.

## 3. Give it a fixed address
Without this, the address changes whenever the server restarts and the app can no longer find it.
1. EC2 → **Elastic IPs** (left menu) → **Allocate Elastic IP address** → **Allocate**.
2. Select the new address → **Actions → Associate Elastic IP address** → choose `stock-list-server` → **Associate**.
3. Note the address (for example `13.200.10.20`).

## 4. Connect GitHub to it
In the GitHub repository → **Settings → Secrets and variables → Actions**:
- **Variables** tab → **New repository variable**: `DEPLOY_HOST` = the Elastic IP address.
- **Secrets** tab: `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `DEPLOY_SSH_KEY` (from the secrets file
  you were given), if not added yet.

## 5. Install the backend and build the app
1. GitHub → **Actions** → **Deploy backend** → **Run workflow**. It runs the tests, installs everything and waits until
   `https://<ip-with-dashes>.sslip.io/` answers. Green = the server works.
2. **Actions** → **Android release APK** → **Run workflow**. Download `Stock-List-Manager-release-APK` from the run,
   unzip, install on the phones. The owner opens the app → **Register your business**.

## Checking the server
- Browser terminal: EC2 → select the instance → **Connect** → **EC2 Instance Connect** → **Connect**.
- Setup result: `sudo cat /root/stocklist-setup.txt`; setup log: `sudo tail -n 50 /var/log/cloud-init-output.log`
- Service status: `cd /opt/stocklist && docker compose -f docker-compose.prod.yml ps`
- Credits left: AWS console → search **Billing** → **Credits** (or the Free plan banner on the console home).

## Before month 6
Pick one, about a month before the end:
- **Stay on AWS**: switch the account to the **Paid plan** (Billing → *Upgrade plan*). Nothing changes on the server;
  you then pay for it monthly (the same server costs roughly $15–20 a month).
- **Move to a cheaper server** (for example an Indian VPS at ₹600–800/month): create it with `server-setup.sh`, run
  `./backup.sh` on AWS, copy the backup across and restore it ([BACKUP_RESTORE.md](BACKUP_RESTORE.md) → "Moving to a
  new server"), set `DEPLOY_HOST` to the new IP, run **Deploy backend**, then **Android release APK** and install the
  new APK (its server address changes with the IP unless you use your own domain).

Tip: with your own domain (`API_BASE_URL`), moving servers later needs no new APK; only the DNS record changes.
