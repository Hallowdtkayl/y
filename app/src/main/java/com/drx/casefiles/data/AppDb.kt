package com.drx.casefiles.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Patient::class, Visit::class, Media::class, Diagnosis::class, Mark::class,
        Vital::class, MedOrder::class, RefRange::class, LabValue::class, Procedure::class, ProcOption::class
    ],
    version = 10,
    exportSchema = false
)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): CaseDao

    companion object {
        @Volatile
        private var instance: AppDb? = null

        /**
         * 6 -> 7: case file sections. Patient gets the probable diagnosis and the medical history
         * (copied from the latest admission that has any), plus the procedures and smart-dropdown tables.
         * Nothing is deleted.
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                listOf("probableDiagnosis", "pmh", "psh", "drugs", "allergy", "family", "habits").forEach { c ->
                    db.execSQL("ALTER TABLE `patients` ADD COLUMN `$c` TEXT NOT NULL DEFAULT ''")
                }
                listOf("pmh", "psh", "drugs", "allergy", "family").forEach { c ->
                    db.execSQL(
                        "UPDATE `patients` SET `$c` = COALESCE((SELECT a.`$c` FROM `admissions` a " +
                            "WHERE a.`patientId` = `patients`.`id` AND a.`$c` != '' ORDER BY a.`admitDate` DESC LIMIT 1), '')"
                    )
                }
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `procedures` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`visitId` INTEGER NOT NULL, `type` TEXT NOT NULL, `region` TEXT NOT NULL, `side` TEXT NOT NULL, " +
                        "`bone` TEXT NOT NULL, `status` TEXT NOT NULL, `choice` TEXT NOT NULL, `position` TEXT NOT NULL, " +
                        "`detail` TEXT NOT NULL, `time` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`visitId`) REFERENCES `visits`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_procedures_visitId` ON `procedures` (`visitId`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `proc_options` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`field` TEXT NOT NULL, `text` TEXT NOT NULL)"
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_proc_options_field_text` ON `proc_options` (`field`, `text`)")
            }
        }

        /** 7 -> 8: the section a procedure row belongs to, and the consultation a media item is linked to. */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `procedures` ADD COLUMN `section` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `media` ADD COLUMN `procedureId` INTEGER")
            }
        }

        /**
         * 8 -> 9: admissions and the old per-limb exam are gone. What they held that the new sections also hold
         * is moved into procedure rows first (gross note, needs, urinary catheter, vascular / neuro exam), then the
         * visits table is rebuilt without the retired columns. The old admissions and exams tables are left in the
         * file untouched (unused archive) so nothing is destroyed.
         */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val cols = "(visitId, type, section, region, side, bone, status, choice, position, detail, time, createdAt)"
                fun fromVisits(type: String, section: String, status: String, detail: String, where: String) = db.execSQL(
                    "INSERT INTO procedures $cols SELECT id, '$type', '$section', '', 'NONE', '', '$status', '', '', $detail, '', 0 FROM visits WHERE $where"
                )
                fromVisits("GROSS", "PROCEDURE", "DONE", "gross", "gross != ''")
                fromVisits("BLOOD_UNITS", "SUPPLY", "PENDING", "''", "needBlood = 1")
                fromVisits("PLASMA_UNITS", "SUPPLY", "PENDING", "''", "needPlasma = 1")
                fromVisits("CONSULT", "CONSULT", "PENDING", "''", "needConsult = 1")
                fromVisits("SUPPLY_OTHER", "SUPPLY", "PENDING", "needsNote", "needsNote != ''")
                fromVisits(
                    "CATHETER", "PROCEDURE", "DONE",
                    "CASE catheter WHEN 'EMPTY' THEN 'فارغ' WHEN 'HALF' THEN 'نصف ممتلئ' ELSE 'ممتلئ' END",
                    "catheter IN ('EMPTY', 'HALF', 'FULL')"
                )
                val vascular = "CASE e.vascular WHEN 'NORMAL' THEN 'Normal · طبيعي' WHEN 'MODERATE' THEN 'Moderate · متوسط' " +
                    "WHEN 'WEAK' THEN 'Weak · ضعيف' WHEN 'NOT_PALPABLE' THEN 'Pulse not palpable (cast) · تعذر جس النبض بسبب الجبس' " +
                    "WHEN 'ABSENT' THEN 'Absent · معدوم' ELSE e.vascular END"
                val neuro = "CASE e.neuro WHEN 'NORMAL' THEN 'Normal · طبيعي' WHEN 'ABNORMAL' THEN 'Abnormal · غير طبيعي' " +
                    "WHEN 'ABSENT' THEN 'Absent · معدوم' WHEN 'UNCOOPERATIVE' THEN 'Patient uncooperative · المريض غير متجاوب أو متعاون' " +
                    "WHEN 'PARALYSIS_OLD' THEN 'Paralysis from previous disease · شلل نتيجة مرض سابق' ELSE e.neuro END"
                fun fromExams(type: String, choice: String, detail: String, where: String) = db.execSQL(
                    "INSERT INTO procedures $cols SELECT e.visitId, '$type', 'PROCEDURE', e.region, e.side, '', 'DONE', $choice, '', $detail, '', 0 " +
                        "FROM exams e WHERE $where"
                )
                fromExams("VASCULAR", vascular, "''", "e.vascular != ''")
                fromExams("NEURO", neuro, "''", "e.neuro != ''")
                fromExams("GROSS", "''", "e.note", "e.note != ''")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `visits_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `patientId` INTEGER NOT NULL, " +
                        "`kind` TEXT NOT NULL, `title` TEXT NOT NULL, `date` INTEGER NOT NULL, `notes` TEXT NOT NULL, " +
                        "`generalCondition` TEXT NOT NULL, `vitalsStable` TEXT NOT NULL, `keywords` TEXT NOT NULL, " +
                        "`labText` TEXT NOT NULL, `extras` TEXT NOT NULL, " +
                        "FOREIGN KEY(`patientId`) REFERENCES `patients`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL(
                    "INSERT INTO `visits_new` (id, patientId, kind, title, date, notes, generalCondition, vitalsStable, keywords, labText, extras) " +
                        "SELECT id, patientId, kind, title, date, notes, generalCondition, vitalsStable, keywords, labText, extras FROM `visits`"
                )
                db.execSQL("DROP TABLE `visits`")
                db.execSQL("ALTER TABLE `visits_new` RENAME TO `visits`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_visits_patientId` ON `visits` (`patientId`)")
            }
        }

        /** 9 -> 10: habits and instincts (smoking, alcohol, urination, defecation, sleep, eating) on the patient. */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                listOf("smoking", "alcohol", "urination", "defecation", "sleep", "eating").forEach { c ->
                    db.execSQL("ALTER TABLE `patients` ADD COLUMN `$c` TEXT NOT NULL DEFAULT ''")
                }
            }
        }

        fun get(context: Context): AppDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDb::class.java, "cases.db")
                .addMigrations(MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
                .fallbackToDestructiveMigrationFrom(1, 2, 3, 4, 5).build().also { instance = it }
        }
    }
}
