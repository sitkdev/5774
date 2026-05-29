import gspread
from google.oauth2.service_account import Credentials

from .constants import GSHEET_ID, SHEET_TEMPLATE_TAB

SCOPES = [
    "https://www.googleapis.com/auth/spreadsheets",
    "https://www.googleapis.com/auth/drive",
]


class SheetsClient:
    def __init__(self, credentials_path: str):
        creds = Credentials.from_service_account_file(credentials_path, scopes=SCOPES)
        self.gc = gspread.authorize(creds)
        self.sh = self.gc.open_by_key(GSHEET_ID)

    def duplicate_template(self, new_tab_name: str):
        try:
            existing = self.sh.worksheet(new_tab_name)
            print(f"  Tab '{new_tab_name}' already exists — reusing it.")
            return existing
        except gspread.WorksheetNotFound:
            pass
        template = self.sh.worksheet(SHEET_TEMPLATE_TAB)
        return template.duplicate(new_sheet_name=new_tab_name)

    def fill_metadata(
        self,
        tab: str,
        app_name: str,
        subtitle: str,
        description: str,
        keywords: str,
        privacy_url: str,
        support_url: str,
    ):
        ws = self.sh.worksheet(tab)
        # Row 1 = headers; row 2 = en-US (auto-translated into rows 3-37).
        # Columns: A=locale B=name C=subtitle D=description E=keywords
        #          F=release_notes (skip) G=promotional_text (skip)
        #          H=privacy_url I=support_url
        updates = [
            ("B2", app_name),
            ("C2", subtitle),
            ("D2", description),
            ("E2", keywords),
            ("H2", privacy_url),
            ("I2", support_url),
        ]
        ws.batch_update(
            [{"range": cell, "values": [[value]]} for cell, value in updates],
            value_input_option="RAW",
        )
        for cell, value in updates:
            print(f"  ✓ {cell} ← {_short(value)}")


def _short(s: str) -> str:
    s = str(s).replace("\n", " ")
    return s if len(s) <= 60 else s[:57] + "..."
