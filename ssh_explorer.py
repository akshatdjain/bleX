import paramiko
import os

def run():
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    try:
        ssh.connect('pi5', username='pi5', password='1234')
        
        files_to_read = [
            'README.md',
            'config.py',
            'fifo_consumer.py',
            'logger.py',
            'master.py',
            'master_logger.py',
            'master_register.py',
            'master_stack.sh',
            'master_updated.py',
            'master_updated_timestamp.py',
            'main.py'
        ]
        
        os.makedirs("pi_files", exist_ok=True)
        
        for f in files_to_read:
            stdin, stdout, stderr = ssh.exec_command(f'cat ~/master/{f}')
            content = stdout.read()
            if content:
                with open(f"pi_files/{f}", "wb") as out_file:
                    out_file.write(content)
                print(f"Saved pi_files/{f}")
            else:
                print(f"Empty or not found: {f}")
                
    except Exception as e:
        print(f"Error: {e}")
    finally:
        ssh.close()

if __name__ == '__main__':
    run()
