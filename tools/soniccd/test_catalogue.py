#!/usr/bin/env python3
"""Parser and correspondence regressions for the 2026-10-23 Sonic CD catalogue.

Run with python3 -B -m unittest discover -s tools/soniccd -p test_catalogue.py.
Source parity cases require the optional local scddisasm checkout, never retail media.
"""
from __future__ import annotations

import unittest
import catalogue


class DeclarationTests(unittest.TestCase):
    def test_numbering_includes_unnamed_invalid_slot(self):
        text = "SCMD_FIRST rs.b 1\n  rs.b 1 ; Invalid\nSCMD_LAST rs.b 1\n"
        self.assertEqual(catalogue.declarations(text, "SCMD_", 38), [
            {"symbol": "FIRST", "id": 38}, {"symbol": "INVALID_39", "id": 39},
            {"symbol": "LAST", "id": 40},
        ])

    def test_comments_do_not_create_active_slots(self):
        text = "; FID_UNUSED rs.b 1\nFID_REAL rs.b 1 ; real slot\n rsset 0\n"
        self.assertEqual(catalogue.declarations(text, "FID_", 0), [{"symbol": "REAL", "id": 0}])

    def test_unsupported_declarations_fail_instead_of_shifting_slots(self):
        for text in ("FID_RANGE rs.b 2", "SCMD_OTHER rs.b 1"):
            with self.subTest(text=text), self.assertRaises(ValueError):
                catalogue.declarations(text, "FID_", 0)

    def test_regional_selection_respects_else_and_elseif(self):
        text = 'if REGION=JAPAN\nJ\nelseif REGION=USA\nU\nelse\nE\nendif\nshared'
        self.assertEqual(catalogue.selected_lines(text, "REGION", "USA"), "U\nshared")
        self.assertEqual(catalogue.selected_lines(text, "REGION", "JAPAN"), "J\nshared")
        self.assertEqual(catalogue.selected_lines(text, "REGION", "EUROPE"), "E\nshared")

    def test_unsupported_or_broken_conditionals_fail(self):
        for text in ("if REGION=JAPAN\nx", "if REGION<>USA\nx\nendif",
                     "if DATAFILE=0\nx\nendif", "if REGION=USA\nif REGION=USA\nx\nendif\nendif",
                     "elseif REGION=USA\nx\nendif", "else\nx", "endif"):
            with self.subTest(text=text), self.assertRaises(ValueError):
                catalogue.selected_lines(text, "REGION", "USA")

    def test_duplicate_case_detection_does_not_silently_overwrite(self):
        with self.assertRaises(ValueError):
            catalogue.switch_cases("case A -> Kind.ONE; case A -> Kind.TWO;", r"Kind\.(\w+)")


@unittest.skipUnless((catalogue.ROOT / "docs/scddisasm/src/_Include/System.inc").is_file(),
                     "Optional scddisasm source is unavailable")
class SourceParityTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.data = catalogue.inventory(catalogue.ROOT / "docs/scddisasm")
        cls.texts = {p.stem: p.read_text() for p in catalogue.JAVA.glob("*.java")}

    def test_current_catalogue_matches_all_source_tables(self):
        catalogue.check(self.data, self.texts)
        self.assertEqual(len(self.data["files"]), 127)
        self.assertEqual(len(self.data["commands"]), 216)
        by_command = {x["symbol"]: x for x in self.data["commands"]}
        self.assertEqual(by_command["INVALID_39"]["load_files"], ["R11A", "R11A"])
        self.assertEqual(by_command["R33C"]["load_files"], ["R33C", "R3PCM"])
        self.assertEqual(by_command["R83D"]["load_files"], ["R83D", "BOSSPCM"])
        self.assertEqual(by_command["TIMEATK"]["load_files"], ["TIMEATKMAIN"])
        for name in ("DUMMY1", "DUMMY2", "DUMMY3", "DUMMY4", "DUMMY5"):
            self.assertEqual(by_command[name]["load_files"], ["MCSONIC"])
            self.assertEqual(by_command[name]["payload_alias_of"], "MCSONIC")
        by_file = {x["symbol"]: x for x in self.data["files"]}
        self.assertEqual(by_file["TIMEATKSUB"]["disposition"], "NO_SPX_LOAD_BINDING")
        self.assertEqual(by_file["FINALPCM"]["disposition"], "NO_SPX_LOAD_BINDING")
        self.assertEqual(by_file["DUMMY9"]["copy_source"], "DUMMY5")

    def assert_rejected(self, file, old, new):
        self.assertTrue(old in self.texts[file], f"Missing mutation target in {file}: {old}")
        modified = dict(self.texts)
        modified[file] = modified[file].replace(old, new, 1)
        with self.assertRaises(ValueError):
            catalogue.check(self.data, modified)

    def test_missing_nonstage_loader_is_rejected(self):
        self.assert_rejected("CdNativeProgram", "    TIMEATK(CdCommand.TIMEATK, CdNativeFile.TIMEATKMAIN),\n", "")

    def test_invented_time_attack_subcomponent_is_rejected(self):
        self.assert_rejected("CdNativeProgram", "TIMEATK(CdCommand.TIMEATK, CdNativeFile.TIMEATKMAIN)",
                             "TIMEATK(CdCommand.TIMEATK, CdNativeFile.TIMEATKMAIN, CdNativeFile.TIMEATKSUB)")

    def test_wrong_stage_pcm_is_rejected(self):
        self.assert_rejected("CdStagePayload", "R83D(CdNativeFile.BOSSPCM)", "R83D(CdNativeFile.FINALPCM)")

    def test_wrong_command_identity_and_missing_invalid_slot_are_rejected(self):
        self.assert_rejected("CdCommand", 'R11A(1, "SPCmd_LoadLevel")', 'R11A(0, "SPCmd_LoadLevel")')
        self.assert_rejected("CdCommand", '    INVALID_39(39, "SPCmd_LoadLevel"),\n', "")

    def test_wrong_native_filename_or_role_is_rejected(self):
        self.assert_rejected("CdNativeFile", 'BADENDSUB(109, "GOODEND.BIN;1"', 'BADENDSUB(109, "BADEND.BIN;1"')
        self.assert_rejected("CdNativeFile", 'DAGARDDATA(99, "PLANET_D.BIN;1", Kind.DATA)',
                             'DAGARDDATA(99, "PLANET_D.BIN;1", Kind.SUB_CPU_PROGRAM)')

    def test_wrong_ending_stream_is_rejected(self):
        self.assert_rejected("CdNativeProgram", "case BADEND -> List.of(CdNativeFile.BADENDSTM)",
                             "case BADEND -> List.of(CdNativeFile.GOODENDSTM)")

    def test_wrong_alias_and_copy_origins_are_rejected(self):
        self.assert_rejected("CdNativeProgram", "Optional.of(MCSONIC)", "Optional.of(SNDTEST)")
        self.assert_rejected("CdNativeFile", "Optional.of(DUMMY5)", "Optional.of(MCSONIC)")

    def test_wrong_release_header_is_rejected(self):
        self.assert_rejected("CdReleaseVariant", '"SEGAIPSAMP"', '"SONIC_CD___"')

    def test_missing_file_disposition_and_wrong_audio_disposition_are_rejected(self):
        self.assert_rejected("CdProgramCatalogue", "case TIMEATKSUB, FINALPCM ->", "case TIMEATKSUB ->")
        self.assert_rejected("CdProgramCatalogue", "UNPAUSECDA -> CommandDisposition.AUDIO",
                             "UNPAUSECDA -> CommandDisposition.BACKUP_RAM")

    def test_invented_invalid_slot_pcm_is_rejected(self):
        self.assert_rejected("CdProgramCatalogue", "List.of(CdNativeFile.R11A, CdNativeFile.R11A)",
                             "List.of(CdNativeFile.R11A, CdNativeFile.R1PCM)")

    def test_sound_test_pcm_dependencies_follow_all_seventeen_handlers(self):
        actual = {x["symbol"]: x["load_files"] for x in self.data["commands"]
                  if x["disposition"] == "AUDIO" and x["load_files"]}
        self.assertEqual(actual, {
            "FUTURESFXT": ["R1PCM"], "PASTSFXT": ["R1PCM"], "ALRGHTSFXT": ["R1PCM"],
            "GIVEUPSFXT": ["R1PCM"], "YESSFXT": ["R1PCM"], "YEAHSFXT": ["R1PCM"],
            "GIGGLESFXT": ["R1PCM"], "YELPSFXT": ["R1PCM"],
            "STOMPSFXT": ["R3PCM"], "BUMPERSFXT": ["R3PCM"],
            "R1BMUST": ["R1PCM"], "R3BMUST": ["R3PCM"], "R4BMUST": ["R4PCM"],
            "R5BMUST": ["R5PCM"], "R6BMUST": ["R6PCM"], "R7BMUST": ["R7PCM"],
            "R8BMUST": ["R8PCM"],
        })

    def test_missing_or_wrong_sound_test_pcm_target_is_rejected(self):
        self.assertEqual(17, sum(x["disposition"] == "AUDIO" and bool(x["load_files"])
                                 for x in self.data["commands"]))
        for entry in self.data["commands"]:
            if entry["disposition"] != "AUDIO" or not entry["load_files"]:
                continue
            command, driver = entry["symbol"], entry["load_files"][0]
            binding = f"Map.entry(CdCommand.{command}, CdNativeFile.{driver})"
            with self.subTest(command=command, mutation="missing"):
                self.assert_rejected("CdProgramCatalogue", binding, "")
            with self.subTest(command=command, mutation="wrong"):
                self.assert_rejected("CdProgramCatalogue", binding,
                                     f"Map.entry(CdCommand.{command}, CdNativeFile.BOSSPCM)")

    def test_sound_test_pcm_dependencies_cannot_be_dropped_at_the_return(self):
        self.assert_rejected("CdProgramCatalogue", "return List.of(pcmDriver);", "return List.of();")

    def test_stage_disposition_return_mutation_is_rejected(self):
        self.assert_rejected("CdProgramCatalogue", "return CommandDisposition.STAGE_LOAD;",
                             "return CommandDisposition.AUDIO;")

    def test_program_and_alias_disposition_return_mutations_are_rejected(self):
        self.assert_rejected("CdProgramCatalogue", "default -> CommandDisposition.PROGRAM_LOAD;",
                             "default -> CommandDisposition.AUDIO;")
        self.assert_rejected("CdProgramCatalogue", "return CommandDisposition.UNUSED_PAYLOAD_ALIAS;",
                             "return CommandDisposition.PROGRAM_LOAD;")

    def test_disposition_branch_condition_mutations_are_rejected(self):
        for condition in ("stageForCommand(command).isPresent()", "program.isPresent()",
                          "program.get().payloadAliasOf().isPresent()"):
            with self.subTest(condition=condition):
                self.assert_rejected("CdProgramCatalogue", f"if ({condition})", f"if (!{condition})")

    def test_uncatalogued_command_and_file_defaults_are_guarded(self):
        self.assert_rejected("CdProgramCatalogue",
                             'default -> throw new IllegalStateException("Uncatalogued native command: " + command);',
                             "default -> CommandDisposition.AUDIO;")
        self.assert_rejected("CdProgramCatalogue", "default -> FileDisposition.SPX_LOAD_COMPONENT;",
                             "default -> FileDisposition.NO_SPX_LOAD_BINDING;")


if __name__ == "__main__":
    unittest.main()
