# GitHub Push Protection: Troubleshooting & Fix

## The Issue
When attempting to push the local Git repository to GitHub using `git push`, the command failed with the following error:

```text
remote: error: GH013: Repository rule violations found for refs/heads/master.
remote: - GITHUB PUSH PROTECTION
remote:   —————————————————————————————————————————
remote:     Resolve the following violations before pushing again
remote:     - Push cannot contain secrets
remote:       —— Google OAuth Client ID & Secret ——————
```

### Why did this happen?
GitHub has a built-in security feature called **Secret Scanning**. It continuously monitors commits being pushed to repositories. If it detects recognizable patterns of sensitive information (like AWS keys, database passwords, or in this case, **Google OAuth Client IDs and Secrets**), it actively blocks the push.

This happened because real Google OAuth credentials were saved in the `src/main/resources/application.yml` file during earlier development commits. GitHub blocked the push to prevent those credentials from being leaked to the public internet where malicious bots constantly scrape for exposed keys.

---

## The Solution

To fix this, we couldn't just delete the keys from the file and make a new commit. Git keeps a full history of all past commits; therefore, if we simply pushed, the blocked secrets would still exist in the Git history. We needed to remove them from the history entirely.

Here is the step-by-step process of how the issue was fixed:

### Step 1: Remove the Secrets from the File
First, the real credentials in `application.yml` were replaced with placeholder dummy text:
```yaml
      client:
        registration:
          google:
            client-id: YOUR_GOOGLE_CLIENT_ID
            client-secret: YOUR_GOOGLE_CLIENT_SECRET
```

### Step 2: Soft Reset the Git History
Because the secret was added a few commits back (specifically in a commit named *"Part 8 - OAuth2 Client added"*), we performed a **Soft Reset**. 

A soft reset rewinds the Git timeline back to a specific commit *without* deleting any of the actual code changes in the files. 

```powershell
git reset --soft bc8cac7
```
*(Where `bc8cac7` was the hash of the commit made immediately **before** the secrets were introduced).*

### Step 3: Squash and Re-commit
After the soft reset, all the changes across the previous commits were placed into the "Staging Area". Because `application.yml` had been scrubbed of the secrets in Step 1, the staged files were now perfectly clean.

All the changes were then bundled into a brand new, single commit:
```powershell
git add .
git commit -m "Added OAuth2 Client, Session Management, Roles, and Docs"
```

### Step 4: Push Successfully
With the clean Git history containing no trace of the real Google credentials, the push to GitHub succeeded seamlessly:
```powershell
git push -u origin master
```

---

## Best Practices Moving Forward
To prevent this in the future:
1. **Never commit real secrets:** Always use environment variables for sensitive data in your properties/yml files:
   ```yaml
   client-secret: ${GOOGLE_CLIENT_SECRET}
   ```
2. **Use `.env` files:** Store your real secrets in an `.env` file locally, and ensure `.env` is added to your `.gitignore` file so it is never tracked by Git.
