# Amazon Q Configuration

## Overview
This workspace is configured to use Amazon Q with Bedrock as the AI model backend.

## Configuration Files

### 1. **`.env`** (Workspace-level)
Located in the project root, contains environment variables for Amazon Q:
- `AWS_ACCESS_KEY_ID` - AWS credentials
- `AWS_SECRET_ACCESS_KEY` - AWS secret
- `AWS_DEFAULT_REGION` - AWS region (us-east-2)
- `BEDROCK_MODEL_ID` - Claude Sonnet 4.5 model ARN
- `BEDROCK_EMBEDDING_MODEL` - Titan embedding model

**⚠️ SECURITY:** This file is in `.gitignore` and should NEVER be committed to Git.

### 2. **`~/.aws/credentials`** (User-level)
Located in your home directory (`/home/akshat/.aws/credentials`), stores AWS credentials:
- Used by AWS CLI and SDK tools
- Can be used by Amazon Q as fallback

### 3. **`~/.aws/config`** (User-level)
Located in your home directory (`/home/akshat/.aws/config`), stores AWS region settings.

### 4. **`.vscode/settings.json`** (Workspace-level)
Contains VS Code and Amazon Q extension settings:
- Bedrock model configuration
- AWS region settings
- Amazon Q behavior preferences

## How It Works

When you use Amazon Q in VS Code:
1. VS Code loads environment variables from `.env`
2. Falls back to `~/.aws/credentials` and `~/.aws/config` if needed
3. Uses settings from `.vscode/settings.json` for extension behavior
4. Connects to AWS Bedrock service with Anthropic Claude Sonnet 4.5 model

## Models Configured

- **LLM Model**: Claude Sonnet 4.5 (inference profile)
  - ARN: `arn:aws:bedrock:us-east-2:535002855311:inference-profile/global.anthropic.claude-sonnet-4-5-20250929-v1:0`
- **Embedding Model**: Amazon Titan Embed Text v2
  - Used for code search and context retrieval

## Credential Rotation

If you need to update credentials:
1. Update `.env` file
2. Update `~/.aws/credentials` (optional)
3. Restart VS Code for changes to take effect

## Troubleshooting

If Amazon Q isn't working:
1. Verify `.env` file exists in workspace root
2. Check `~/.aws/credentials` file permissions: `chmod 600 ~/.aws/credentials`
3. Test AWS credentials: `aws sts get-caller-identity`
4. Restart VS Code completely
5. Check Amazon Q extension logs: View → Output → Amazon Q

## Security Best Practices

✅ **DO:**
- Keep `.env` in `.gitignore`
- Use IAM roles for production systems
- Rotate credentials regularly
- Use MFA for AWS account

❌ **DON'T:**
- Commit `.env` to version control
- Share credentials publicly
- Use root AWS account credentials
- Store plaintext credentials in code comments
