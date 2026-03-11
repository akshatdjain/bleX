import paramiko
import os

def run():
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    try:
        ssh.connect('pi5', username='pi5', password='1234')
        
        # 1. Upload the file using SFTP
        sftp = ssh.open_sftp()
        local_path = r'c:\Users\Inno\Desktop\blegod\pi_files\master_updated_timestamp.py'
        remote_path = '/home/pi5/master/master_updated_timestamp.py'
        
        print(f"Uploading {local_path} to {remote_path}...")
        sftp.put(local_path, remote_path)
        sftp.close()
        print("Upload successful.")
        
        # 2. Restart the master process (kill old, start new via master_stack.sh or just kill so stack restarts if there's a loop)
        # Actually the user has master_stack.sh which starts the background process. 
        # But wait, looking at master_stack.sh, it just runs it in the background and saves PID.
        # Let's cleanly kill the old master process and restart the stack.
        print("Killing old master processes...")
        ssh.exec_command('pkill -f "python3 -u master.py"')
        ssh.exec_command('pkill -f "python3 -u master_updated_timestamp.py"')
        
        # Then let the user manually reboot or we can run the script via nohup
        # The user's master_stack.sh runs "python3 -u master.py" which is weird, because the current file is master_updated_timestamp.py.
        # Let's check master_stack.sh
        stdin, stdout, stderr = ssh.exec_command('cat /home/pi5/master/master_stack.sh')
        stack_sh = stdout.read().decode()
        if "master_updated_timestamp.py" not in stack_sh:
             print("Warning: master_stack.sh runs master.py, not master_updated_timestamp.py!")
             
    except Exception as e:
        print(f"Error: {e}")
    finally:
        ssh.close()

if __name__ == '__main__':
    run()
