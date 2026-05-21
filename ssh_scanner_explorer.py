import paramiko
import os

def run():
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    try:
        ssh.connect('pi5', username='pi5', password='1234')
        
        files_to_read = [
            'config.py',
            'kalman.py',
            'scanner.py',
            'scanner_boot.py'
        ]
        
        os.makedirs("pi_scanner_files", exist_ok=True)
        
        for f in files_to_read:
            stdin, stdout, stderr = ssh.exec_command(f'cat ~/scanner/{f}')
            content = stdout.read()
            if content:
                with open(f"pi_scanner_files/{f}", "wb") as out_file:
                    out_file.write(content)
                print(f"Saved pi_scanner_files/{f}")
            else:
                print(f"Empty or not found: {f}")
                
    except Exception as e:
        print(f"Error: {e}")
    finally:
        ssh.close()

if __name__ == '__main__':
    run()
