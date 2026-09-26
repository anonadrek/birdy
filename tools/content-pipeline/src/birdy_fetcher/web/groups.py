"""Family to group mapping (the app's family_groups.yaml) and the web's group table."""

from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import yaml


@dataclass(frozen=True)
class WebGroup:
    key: str
    slug_sv: str
    slug_en: str
    name_sv: str
    name_en: str
    photo: str


class GroupTable:
    """Same 15 groups as the app's field guide, plus the web's slugs and names."""

    def __init__(self, family_groups_yaml: Path, web_groups_json: Path) -> None:
        raw: dict[str, Any] = yaml.safe_load(family_groups_yaml.read_text(encoding="utf-8"))
        self.order: list[str] = list(raw["order"])
        self._order_key = ""
        self._order_group = ""
        self._family_to_group: dict[str, str] = {}
        for key, spec in raw["groups"].items():
            if spec.get("keyed_by") == "order":
                self._order_key = spec["ioc_order"]
                self._order_group = key
            for family in spec.get("families", []):
                self._family_to_group[family] = key

        web: dict[str, Any] = json.loads(web_groups_json.read_text(encoding="utf-8"))
        self.groups = [
            WebGroup(
                key=g["key"],
                slug_sv=g["slug"]["sv"],
                slug_en=g["slug"]["en"],
                name_sv=g["name"]["sv"],
                name_en=g["name"]["en"],
                photo=g["photo"],
            )
            for g in web["groups"]
        ]
        self.common: list[str] = list(web["common"])
        keys = [g.key for g in self.groups]
        if keys != self.order:
            raise ValueError(
                f"species-groups.json har inte samma grupper i samma ordning som appen: "
                f"{keys} mot {self.order}"
            )

    def group_for(self, *, family: str, ioc_order: str) -> str:
        if ioc_order == self._order_key:
            return self._order_group
        return self._family_to_group.get(family, "other")

    def by_key(self, key: str) -> WebGroup:
        for group in self.groups:
            if group.key == key:
                return group
        raise KeyError(key)
