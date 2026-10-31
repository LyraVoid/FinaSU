---
title: Dark Mode, Colors & Titles
icon: material-symbols:palette-outline
description: FinaSU light and dark mode, accent color, color standard, color style, wallpaper extraction and top bar title
createTime: 2026/10/24 00:00:00
permalink: /en/theme/global/theme-style/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# Dark Mode, Colors & Titles

<Badge text="Material 3 Expressive" type="tip" /> <Badge text="Wallpaper extraction can override the seed" type="warning" /> <Badge text="No separate contrast UI" type="info" />

Color settings live in Settings → Appearance → Theme settings. They control the Material palette, light and dark mode and the top bar title of the Manager; they do not change root authorization or system-level theming.

## Light and Dark Mode

:::: table Interface options

| Option | Behavior |
| --- | --- |
| Follow system | Uses dark colors when the system is dark, light colors otherwise |
| Light | Forces the light palette |
| Dark | Forces the dark palette |
| AMOLED pure black theme | Appears only in dark mode; sets the main background and surfaces to pure black |

::::

AMOLED is an additional switch inside dark mode, not a fourth independent mode. Switching back to light, or the system being in light mode, does not keep pure black surfaces.

## Accent Color

The first item in the color picker, Follow Light, corresponds to `keyColor = 0`. Choosing it allows system dynamic color extraction again; choosing a specific swatch saves that ARGB value and turns this dynamic path off.

The interface offers 15 directly selectable colors:

```text
Red Glow  Peach Blossom  Wisteria  Deep Night  Indigo  Blue Wave  Clear Sky
Jade Pool  Bamboo Green  Molten Gold  Amber  Orange Glow  Agarwood  Distant Peak  Cherry Snow
```

The theme protocol additionally accepts 5 FolkPatch color tokens:

```text
light_blue light_green lime deep_orange ink_wash
```

A FinaSU export writes both `customColor` and the exact `finasu_keyColor`. The first gives compatible implementations a recognizable catalog entry; the second lets FinaSU keep arbitrary ARGB precision when the theme is imported back.

:::: warning Wallpaper extraction takes priority over the accent color

When the wallpaper master switch is on, the current page has a usable image background and Extract color from wallpaper is on, the Manager uses the ARGB seed extracted from the wallpaper. The accent color in settings does not override that page seed at this point. In multi-background mode the home screen wallpaper is fixed as the global accent seed, so the whole palette is not regenerated on every page swipe. Video mode hides this extraction switch and does not extract a seed from video frames in real time.

::::

## Color Standards

::::: field-group

:::: field MD3 2021
@type `colorStandard=MD3_2021`
Uses the Material Design 3 2021 color standard. The FinaSU `classic` generation mode also prefers the built-in FolkPatch color table.
::::

:::: field M3E 2025
@type `colorStandard=M3E_2025`
Uses the Material 3 Expressive 2025 color standard. It only applies directly to TonalSpot, Neutral, Vibrant and Expressive.
::::

:::::

When another color style is selected, the 2025 setting falls back to the 2021 standard. The interface still keeps the current `M3E_2025` preference so it can return to a style that supports it later.

## Color Styles

:::: table Nine PaletteStyle values

| Interface name | Theme value | Characteristics |
| --- | --- | --- |
| Soft | `TONAL_SPOT` | The default style, low saturation and balanced |
| Neutral | `NEUTRAL` | Close to grayscale, with a more restrained accent color |
| Vibrant | `VIBRANT` | Higher saturation |
| Expressive | `EXPRESSIVE` | A more vivid Material 3 Expressive palette |
| Rainbow | `RAINBOW` | More pronounced multi-hue variation |
| Fruit Salad | `FRUIT_SALAD` | A livelier combination of several colors |
| Monochrome | `MONOCHROME` | Mainly light and dark levels of the same hue |
| Fidelity | `FIDELITY` | Closer to the seed color itself |
| Content | `CONTENT` | Emphasizes the relationship between content colors and the seed |

::::

These names are the interface strings in the current Simplified Chinese resources and change with the app translations in other languages; theme JSON always uses the stable enumeration values in the table above.

## Generation Mode and Contrast

The theme protocol also supports two fields that affect palette generation but have no separate slider on the current theme settings page:

:::: table Protocol fields

| JSON field | Value | Actual effect |
| --- | --- | --- |
| `colorGenerationMode` | `classic` | Uses the FolkPatch color table when a preset seed exists; uses system dynamic color when there is none |
| `colorGenerationMode` | `custom` | Uses MaterialKolor to generate from the seed, style and standard |
| `colorContrast` | `STANDARD` | Contrast 0.0 |
| `colorContrast` | `MEDIUM` | Contrast 0.5 |
| `colorContrast` | `HIGH` | Contrast 1.0 |

::::

When the user switches the color style or standard, the Manager sets the generation mode to `custom`. Do not look for a nonexistent entry just to see Contrast in the UI; it is currently carried mainly by theme package import, export and internal settings.

## Wallpaper Light and Dark Polarity

Once the wallpaper is enabled, the Manager reads the brightness of the current page background:

- dark text and light text follow the background brightness;
- a dark theme does not flip to a light theme just because the wallpaper is bright;
- dimming improves readability at night;
- a per-page background only changes that page background and content polarity, and the home screen wallpaper is fixed as the global accent seed.

Extract color from wallpaper is a separate switch in Settings → Appearance → Wallpaper. It and the light or dark mode are two different paths: the first decides the seed color, the second decides the light or dark scheme.

## Top Bar Title

The top bar title lives in the Advanced title image area of Settings → Appearance → Layout. The title text has three states:

:::: table Title text modes

| Mode | `titleTextMode` | Display |
| --- | --- | --- |
| App name | `default` | Current app name |
| KernelSU | `kernelsu` | `KernelSU` |
| Custom | `custom` | The text stored in `titleCustomText` |

::::

When `isAdvancedTitleStyleEnabled` is enabled, `title_image.*` replaces the text title. The image display area is about 180×40 dp and uses `FIT_CENTER`, so it is not stretched to arbitrary aspect ratios.

The image can be adjusted:

- day opacity `0..1`;
- night opacity `0..1`;
- dim `0..1`;
- horizontal offset: `-2..2` in JSON, shown as `-200..200 dp` in the interface; the actual displacement is `titleImageOffsetX × 100dp`.

Turning the title image off falls back to the current text mode. The title image can only show when the image itself exists; picking a file enables the title image automatically, and clearing the file disables the feature.

## Restore Defaults

Restore default theme restores:

- follow system;
- `keyColor = 0`;
- system dynamic color available;
- TonalSpot;
- the 2025 color standard;
- `custom` generation mode;
- standard contrast;
- the title image resource is cleared.

The media reset in the current `resetTheme` only turns the title image off and restores the day and night opacity, dim and offset; it does not clear `titleTextMode` or `titleCustomText`. So after restoring, if the title image is off, the top bar may still show the text mode selected earlier.

The ordinary Restore default font does not reset colors at the same time. The complete restore entry is in [Theme Preview & Usage](/en/theme/theme-use/#restore-the-default-theme).

## Related Pages

- [Wallpaper](/en/setting/wallpaper/): background, blur and the color extraction switch.
- [Home & Layout](/en/setting/home/): layout, information hiding and bottom cards.
- [Global Background](/en/theme/global/transparency/): surface transparency and dim.
- [Theme Package Structure](/en/developer/theme-package/): color fields and enumerations.
