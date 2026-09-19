"""Repository-scoped GitHub API helper; credentials stay in process memory."""
import argparse
import json
import subprocess
import urllib.request
import urllib.parse

REPO = "https://api.github.com/repos/DeminGit/rbdip-DeminGIT/"


class SafeRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, msg, headers, newurl):
        redirected = super().redirect_request(request, fp, code, msg, headers, newurl)
        if urllib.parse.urlsplit(request.full_url).netloc != urllib.parse.urlsplit(newurl).netloc:
            redirected.remove_header("Authorization")
        return redirected


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("path")
    parser.add_argument("--method", default="GET", choices=["GET", "POST", "PUT"])
    parser.add_argument("--body-file")
    parser.add_argument("--output")
    args = parser.parse_args()
    if args.path.startswith("/") or ".." in args.path or ":" in args.path:
        parser.error("Only relative repository API paths are allowed")
    credential = subprocess.run(
        ["git", "credential", "fill"],
        input="protocol=https\nhost=github.com\n\n",
        text=True, capture_output=True, check=True,
    )
    fields = dict(line.split("=", 1) for line in credential.stdout.splitlines() if "=" in line)
    data = None
    if args.body_file:
        with open(args.body_file, "rb") as body:
            data = body.read()
    request = urllib.request.Request(REPO + args.path, data=data, method=args.method, headers={
        "Authorization": "Bearer " + fields["password"],
        "Accept": "application/vnd.github+json",
        "Content-Type": "application/json",
        "User-Agent": "rbdip-lab-verification",
    })
    with urllib.request.build_opener(SafeRedirect()).open(request, timeout=60) as response:
        result = response.read()
    if args.output:
        with open(args.output, "wb") as output:
            output.write(result)
        print(args.output)
    else:
        print(json.dumps(json.loads(result), ensure_ascii=True, indent=2))


if __name__ == "__main__":
    main()
