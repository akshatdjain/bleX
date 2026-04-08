import paramiko
import os

# Config from user
HOSTS = ["100.125.23.80", "100.91.37.6"]
USER = "pi5"
PASS = "1234"
REMOTE_PATH = "/opt/asset_tracking/asset_api/routers/zones.py"
LOCAL_PATH = r"c:\Users\Inno\Desktop\blegod\backend\asset_api\routers\zones.py"

def deploy():
    for host in HOSTS:
        print(f"Trying to deploy to {host}...")
        try:
            ssh = paramiko.SSHClient()
            ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
            ssh.connect(host, username=USER, password=PASS, timeout=5)
            
            sftp = ssh.open_sftp()
            sftp.put(LOCAL_PATH, REMOTE_PATH)
            sftp.close()
            
            print(f"Successfully uploaded to {host}:{REMOTE_PATH}")
            ssh.close()
            return True
        except Exception as e:
            print(f"Failed to deploy to {host}: {e}")
    return False

if __name__ == "__main__":
    if deploy():
        print("Deployment successful.")
    else:
        print("Deployment failed on all attempts.")
