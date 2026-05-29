import json
import random

import requests

API = "https://api.telegra.ph"

SUPPORT_CONTENT_VARIANTS = [
    "Contact us at: {email}",
    "For any questions, reach out at {email}.",
    "Have a question? Email us at {email}.",
    "Get in touch: {email}",
    "Need help? Contact us at {email}.",
    "Questions or feedback? Email {email}.",
    "Reach our support team at {email}.",
    "For support, please email us at {email}.",
    "Drop us a line at {email}.",
    "We're here to help - write to {email}.",
]

SUPPORT_TITLE_SUFFIXES = ["", " Support"]


def create_account(short_name: str, author_name: str) -> str:
    short_name = (short_name or "App")[:32] or "App"
    author_name = (author_name or "Author")[:128]
    r = requests.post(
        f"{API}/createAccount",
        data={"short_name": short_name, "author_name": author_name},
        timeout=15,
    )
    r.raise_for_status()
    data = r.json()
    if not data.get("ok"):
        raise SystemExit(f"Telegraph createAccount failed: {data}")
    return data["result"]["access_token"]


def create_support_page(access_token: str, app_name: str, author_name: str, email: str) -> str:
    title = (app_name or "Support") + random.choice(SUPPORT_TITLE_SUFFIXES)
    content_text = random.choice(SUPPORT_CONTENT_VARIANTS).format(email=email)
    return create_page(access_token, title, author_name, content_text)


def create_page(access_token: str, title: str, author_name: str, content_text: str) -> str:
    content = [{"tag": "p", "children": [content_text]}]
    r = requests.post(
        f"{API}/createPage",
        data={
            "access_token": access_token,
            "title": (title or "Support")[:256],
            "author_name": (author_name or "")[:128],
            "content": json.dumps(content),
            "return_content": "false",
        },
        timeout=15,
    )
    r.raise_for_status()
    data = r.json()
    if not data.get("ok"):
        raise SystemExit(f"Telegraph createPage failed: {data}")
    return data["result"]["url"]
