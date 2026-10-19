package com.drx.casefiles.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "patients")
data class Patient(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "",
    val age: String = "",
    val residence: String = "",
    val phone: String = "",
    val complaint: String = "",
    /** When it happened / how long after the injury the patient presented. */
    val delay: String = "",
    /** Mechanism of injury, e.g. fall from 3 m. */
    val mechanism: String = "",
    val diagnosis: String = "",
    val description: String = "",
    /** Section 2: the working (probable) diagnosis, kept apart from the confirmed [diagnosis] of section 1. */
    val probableDiagnosis: String = "",
    /**
     * Section 3, medical history (it used to live on the admission; the 6 -> 7 migration copied it here).
     */
    val pmh: String = "",
    val psh: String = "",
    val drugs: String = "",
    val allergy: String = "",
    val family: String = "",
    /** Other habits (free text). The named ones below are the common ones. */
    val habits: String = "",
    /** Habits and instincts, inside the medical history: free text each, no check box. */
    val smoking: String = "",
    val alcohol: String = "",
    val urination: String = "",
    val defecation: String = "",
    val sleep: String = "",
    val eating: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * One encounter: the first visit (INITIAL), a follow-up (CLINIC review, PHONE verbal follow-up) or a custom
 * one (CUSTOM uses [title]). Older data may still carry the kinds ADMIT, AM, PM and DISCHARGE; they are
 * shown as follow-ups. There are no admissions any more: everything lives in the case file sections.
 */
@Entity(
    tableName = "visits",
    foreignKeys = [ForeignKey(entity = Patient::class, parentColumns = ["id"], childColumns = ["patientId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("patientId")]
)
data class Visit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val patientId: Long,
    val kind: String = "CLINIC",
    val title: String = "",
    val date: Long = System.currentTimeMillis(),
    val notes: String = "",
    val generalCondition: String = "",
    val vitalsStable: String = "",
    /** Key points / hashtags, separated by '|'. */
    val keywords: String = "",
    /** Free text for lab and histology results. */
    val labText: String = "",
    /** Custom titled notes: title\u0001text pairs separated by \u0002. */
    val extras: String = ""
)

@Entity(
    tableName = "vitals",
    foreignKeys = [ForeignKey(entity = Visit::class, parentColumns = ["id"], childColumns = ["visitId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("visitId")]
)
data class Vital(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val visitId: Long,
    val time: String,
    val type: String,
    val value: String
)

/** Drug, blood bag, plasma... with dose, quantity, times per day and time. */
@Entity(
    tableName = "orders",
    foreignKeys = [ForeignKey(entity = Visit::class, parentColumns = ["id"], childColumns = ["visitId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("visitId")]
)
data class MedOrder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val visitId: Long,
    val kind: String = "DRUG",
    val name: String,
    val dose: String = "",
    val qty: String = "",
    val perDay: String = "",
    val time: String = "",
    val given: Boolean = false
)

@Entity(
    tableName = "media",
    foreignKeys = [ForeignKey(entity = Visit::class, parentColumns = ["id"], childColumns = ["visitId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("visitId"), Index("patientId")]
)
data class Media(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val visitId: Long,
    val patientId: Long,
    val path: String,
    val category: String = "CLINICAL",
    val region: String = "OTHER",
    val side: String = "NONE",
    val viewPos: String = "",
    val stage: String = "",
    val healthy: Boolean = false,
    val caption: String = "",
    val takenAt: Long = System.currentTimeMillis(),
    val isVideo: Boolean = false,
    /**
     * The [Procedure] row this image belongs to (the reduction it documents, a consultation request...).
     * For consultations null = shared by the whole visit (merged), a value = kept apart under that one
     * consultation. Plain column, no foreign key; the links are cleared when the procedure is deleted.
     */
    val procedureId: Long? = null
)

/**
 * The diagnosis belongs to (patient, region, side), so it automatically shows on every
 * view (AP, lateral, mortise...) of that body part, and search never mixes in other regions.
 */
@Entity(
    tableName = "diagnoses",
    foreignKeys = [ForeignKey(entity = Patient::class, parentColumns = ["id"], childColumns = ["patientId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["patientId", "region", "side"], unique = true)]
)
data class Diagnosis(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val patientId: Long,
    val region: String,
    val side: String,
    val text: String
)

/** Circle / arrow drawn on an image. Coordinates are 0..1 relative to the image itself. */
@Entity(
    tableName = "marks",
    foreignKeys = [ForeignKey(entity = Media::class, parentColumns = ["id"], childColumns = ["mediaId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("mediaId")]
)
data class Mark(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaId: Long,
    val type: String,
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
    val color: Int,
    val filled: Boolean
)

/** Reference (normal) range of a lab test; editable because ranges differ between labs. */
@Entity(tableName = "ref_ranges")
data class RefRange(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val unit: String = "",
    val low: Double,
    val high: Double
)

@Entity(
    tableName = "lab_values",
    foreignKeys = [ForeignKey(entity = Visit::class, parentColumns = ["id"], childColumns = ["visitId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("visitId")]
)
data class LabValue(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val visitId: Long,
    val name: String,
    val value: String,
    val unit: String = ""
)

/**
 * One line of section 4 (diagnostic means) or section 5 (emergency procedures and ward) of a visit.
 *
 * Check box = [ProcStatus.DONE]: the procedure was done or the item exists (catheter in place, consult done).
 * Unchecked / the (+) button = [ProcStatus.PENDING]: required or waiting, with room for details and measurements
 * ("patent, contains 200 ml"). A procedure can be repeated: add another row for another bone or limb.
 */
@Entity(
    tableName = "procedures",
    foreignKeys = [ForeignKey(entity = Visit::class, parentColumns = ["id"], childColumns = ["visitId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("visitId")]
)
data class Procedure(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val visitId: Long,
    /** [ProcType] name. */
    val type: String,
    /**
     * [ProcGroup] name of the section the row was created in. Empty on older rows: they belong to the
     * default section of their type (see [sectionOf]). This lets neuro / vascular exams live in section 5
     * and in every follow-up without showing the same row twice.
     */
    val section: String = "",
    /** Limb the row refers to (Region name); empty for general items such as a catheter or an ECG. */
    val region: String = "",
    val side: String = "NONE",
    /** Optional bone inside the limb, free text (radius, ulna...). */
    val bone: String = "",
    /** [ProcStatus] name. */
    val status: String = "PENDING",
    /** Value of the smart dropdown that carries the row title (splint type, vascular state...). */
    val choice: String = "",
    /** Second smart dropdown, used by the splint (position). */
    val position: String = "",
    /** Free details or exact measurements. */
    val detail: String = "",
    val time: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

/** An option the user typed into a smart dropdown; it joins the preset list of the same [field] for good. */
@Entity(tableName = "proc_options", indices = [Index(value = ["field", "text"], unique = true)])
data class ProcOption(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Dropdown key, e.g. SPLINT_TYPE; see ProcPresets. */
    val field: String,
    val text: String
)
