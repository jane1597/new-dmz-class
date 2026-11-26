# Reflex Guard 1.7.0 (Forge 1.20.1, needs Epic Fight 20.x)

Automatic Epic Fight parry and dodge. When an enemy attacks you, the mod presses Guard/Parry or Dodge for you
using Epic Fight's OWN skills: real animations, stamina cost, parry stagger, and Epic Fight events (so mods like
Dodge Parry Reward see them).

- Needs a Guard/Parry skill and/or a Dodge skill equipped in Epic Fight (skill screen), and Epic Fight mode on.
- Melee/projectile hits -> Guard/Parry (turns you to face the attacker first). Everything else -> Dodge.
- Config: config/reflexguard-common.toml

Perfect dodge: hits from behind, or while you are attacking, are dodged automatically (plays the dodge animation and cancels your attack).

Growth (config/reflexguard-growth.toml): cropMultiplier, treeMultiplier, breedingCooldownPercent, babyGrowthMultiplier.
