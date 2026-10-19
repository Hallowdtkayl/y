package com.drx.casefiles.data

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import com.drx.casefiles.ui.stringResource
import com.drx.casefiles.R as AppR

enum class Category(@StringRes val label: Int) {
    CLINICAL(AppR.string.cat_clinical),
    XRAY(AppR.string.cat_xray),
    MRI(AppR.string.cat_mri),
    CT(AppR.string.cat_ct),
    ECHO(AppR.string.cat_echo),
    LAB(AppR.string.cat_lab),
    HISTO(AppR.string.cat_histo),
    RX(AppR.string.cat_rx),
    CONSULT(AppR.string.cat_consult),
    REPORT(AppR.string.cat_report)
}

enum class Side(@StringRes val label: Int) {
    NONE(AppR.string.side_none),
    L(AppR.string.side_left),
    R(AppR.string.side_right)
}

enum class Region(@StringRes val label: Int) {
    SHOULDER(AppR.string.r_shoulder), ARM(AppR.string.r_arm), ELBOW(AppR.string.r_elbow),
    FOREARM(AppR.string.r_forearm), WRIST(AppR.string.r_wrist), HAND(AppR.string.r_hand),
    FINGER(AppR.string.r_finger), TOE(AppR.string.r_toe), PELVIS(AppR.string.r_pelvis), HIP(AppR.string.r_hip), THIGH(AppR.string.r_thigh),
    KNEE(AppR.string.r_knee), LEG(AppR.string.r_leg), ANKLE(AppR.string.r_ankle),
    FOOT(AppR.string.r_foot), SPINE(AppR.string.r_spine), CHEST(AppR.string.r_chest), OTHER(AppR.string.r_other)
}

enum class Stage(@StringRes val label: Int) {
    BEFORE_REDUCTION(AppR.string.st_before_reduction),
    AFTER_REDUCTION(AppR.string.st_after_reduction),
    AFTER_CAST(AppR.string.st_after_cast),
    PRE_OP(AppR.string.st_pre_op),
    POST_OP(AppR.string.st_post_op),
    CONTROL_FIXATION(AppR.string.st_control_fixation)
}

enum class ViewPos(@StringRes val label: Int) {
    AP(AppR.string.v_ap), LATERAL(AppR.string.v_lateral), OBLIQUE(AppR.string.v_oblique),
    PA(AppR.string.v_pa), MORTISE(AppR.string.v_mortise), GRASHEY(AppR.string.v_grashey),
    YVIEW(AppR.string.v_yview), AXILLARY(AppR.string.v_axillary), ROBERTS(AppR.string.v_roberts),
    SKYLINE(AppR.string.v_skyline), ANTERIOR(AppR.string.v_anterior), POSTERIOR(AppR.string.v_posterior),
    MEDIAL(AppR.string.v_medial), AXIAL(AppR.string.v_axial), STRESS(AppR.string.v_stress)
}

inline fun <reified T : Enum<T>> enumOrNull(name: String): T? =
    enumValues<T>().firstOrNull { it.name == name }

fun sideShort(side: String): String = when (side) {
    "L" -> "L"
    "R" -> "R"
    else -> ""
}

@Composable
fun stageText(stage: String): String {
    val s = enumOrNull<Stage>(stage)
    return when {
        s != null -> stringResource(s.label)
        stage.isBlank() -> stringResource(AppR.string.stage_none)
        else -> stage
    }
}

@Composable
fun regionText(region: String): String {
    val r = enumOrNull<Region>(region)
    return if (r != null) stringResource(r.label) else ""
}

@Composable
fun viewText(viewPos: String): String {
    val v = enumOrNull<ViewPos>(viewPos)
    return if (v != null) stringResource(v.label) else ""
}

@Composable
fun categoryText(category: String): String {
    val c = enumOrNull<Category>(category)
    return if (c != null) stringResource(c.label) else category
}

/** Label for every visit; clinic follow-ups are numbered per patient by date. */
fun visitLabels(
    ctx: Context,
    visits: List<Visit>,
    tr: (Int, Array<out Any>) -> String = { id, a -> Lang.get(ctx, id, *a) }
): Map<Long, String> {
    val none = emptyArray<Any>()
    val out = HashMap<Long, String>()
    visits.groupBy { it.patientId }.values.forEach { list ->
        var n = 0
        list.sortedBy { it.date }.forEach { v ->
            out[v.id] = when (v.kind) {
                "INITIAL" -> tr(AppR.string.visit_initial, none)
                "ADMIT" -> tr(AppR.string.visit_admit, none)
                "AM" -> tr(AppR.string.visit_am, none)
                "PM" -> tr(AppR.string.visit_pm, none)
                "DISCHARGE" -> tr(AppR.string.visit_discharge, none)
                "PHONE" -> tr(AppR.string.visit_phone, none)
                "CUSTOM" -> v.title.ifBlank { tr(AppR.string.visit_followup, none) }
                else -> {
                    n++
                    tr(AppR.string.visit_followup_fmt, arrayOf<Any>(n))
                }
            }
        }
    }
    return out
}

enum class GeneralCondition(@StringRes val label: Int) {
    GOOD(AppR.string.gc_good), FAIR(AppR.string.gc_fair), POOR(AppR.string.gc_poor)
}

enum class VitalsState(@StringRes val label: Int) {
    STABLE(AppR.string.vs_stable), UNSTABLE(AppR.string.vs_unstable)
}

enum class VitalType(@StringRes val label: Int) {
    BP(AppR.string.vt_bp), PULSE(AppR.string.vt_pulse), SPO2(AppR.string.vt_spo2),
    TEMP(AppR.string.vt_temp), RESP(AppR.string.vt_resp),
    URINE(AppR.string.vt_urine), DRAIN(AppR.string.vt_drain)
}

enum class OrderKind(@StringRes val label: Int) {
    DRUG(AppR.string.ord_drug), BLOOD(AppR.string.ord_blood),
    PLASMA(AppR.string.ord_plasma), OTHER(AppR.string.ord_other)
}

/** 0 = upper limb, 1 = lower limb, 2 = spine / other. */
fun limbGroup(region: String): Int = when (region) {
    "SHOULDER", "ARM", "ELBOW", "FOREARM", "WRIST", "HAND", "FINGER" -> 0
    "PELVIS", "HIP", "THIGH", "KNEE", "LEG", "ANKLE", "FOOT", "TOE" -> 1
    else -> 2
}

@Composable
fun limbText(region: String, side: String): String {
    val g = when (limbGroup(region)) {
        0 -> stringResource(AppR.string.limb_upper)
        1 -> stringResource(AppR.string.limb_lower)
        else -> stringResource(AppR.string.limb_other)
    }
    return (g + " " + sideShort(side)).trim()
}

/** Days since the first visit of the same patient. */
fun dayNumber(visit: Visit, patientVisits: List<Visit>): Int {
    val start = patientVisits.firstOrNull { it.kind == "INITIAL" }?.date
        ?: patientVisits.minOfOrNull { it.date } ?: visit.date
    return ((visit.date - start) / 86_400_000L).toInt().coerceAtLeast(0)
}

// ---------------------------------------------------------------- sections 4 and 5

enum class ProcStatus { PENDING, DONE }

/**
 * Which section a row belongs to: DIAGNOSTIC = 4, PROCEDURE = 5 (emergency procedures and ward),
 * CONSULT = 6 (requested consultations), SUPPLY = 7 (supplies and medical needs),
 * FOLLOWUP = 8 (follow-ups, reviews and surgical outcome).
 */
enum class ProcGroup { DIAGNOSTIC, PROCEDURE, CONSULT, SUPPLY, FOLLOWUP }

/**
 * Kinds of line in sections 4 to 8. [group] is the section the type belongs to; [extraGroups] are further sections
 * that offer it too (neuro and vascular exams are also part of every follow-up). [perLimb] rows ask for a limb so
 * they can be repeated per bone / limb. [choiceKey] / [positionKey] name the smart dropdowns (see [ProcPresets]);
 * blood pressure is not here because it is a list of readings and uses [Vital].
 */
enum class ProcType(
    @StringRes val label: Int,
    val group: ProcGroup,
    val perLimb: Boolean = false,
    val choiceKey: String? = null,
    val positionKey: String? = null,
    val extraGroups: Set<ProcGroup> = emptySet(),
    /** Every row can hold images. Category (and stage) they get by default, so nothing has to be chosen. */
    val imageCat: String = "CLINICAL",
    val imageStage: String = ""
) {
    XRAY(AppR.string.pt_xray, ProcGroup.DIAGNOSTIC, perLimb = true, imageCat = "XRAY"),
    ECG(AppR.string.pt_ecg, ProcGroup.DIAGNOSTIC, imageCat = "REPORT"),
    ABD_ECHO(AppR.string.pt_abd_echo, ProcGroup.DIAGNOSTIC, imageCat = "ECHO"),
    LABS(AppR.string.pt_labs, ProcGroup.DIAGNOSTIC, choiceKey = "LAB_PANEL", imageCat = "LAB"),

    CLOSED_REDUCTION(AppR.string.pt_reduction, ProcGroup.PROCEDURE, perLimb = true, choiceKey = "REDUCTION_METHOD", imageCat = "XRAY", imageStage = "AFTER_REDUCTION"),
    SPLINT(AppR.string.pt_splint, ProcGroup.PROCEDURE, perLimb = true, choiceKey = "SPLINT_TYPE", positionKey = "SPLINT_POSITION", imageCat = "XRAY", imageStage = "AFTER_CAST"),
    GROSS(AppR.string.pt_gross, ProcGroup.PROCEDURE, perLimb = true, choiceKey = "GROSS_FINDING", imageCat = "CLINICAL"),
    PALPATION(AppR.string.pt_palpation, ProcGroup.PROCEDURE, perLimb = true, choiceKey = "PALPATION_FINDING"),
    MANEUVER(AppR.string.pt_maneuver, ProcGroup.PROCEDURE, perLimb = true, choiceKey = "MANEUVER"),
    NEURO(AppR.string.pt_neuro, ProcGroup.PROCEDURE, perLimb = true, choiceKey = "NEURO_STATE", extraGroups = setOf(ProcGroup.FOLLOWUP)),
    VASCULAR(AppR.string.pt_vascular, ProcGroup.PROCEDURE, perLimb = true, choiceKey = "VASCULAR_STATE", extraGroups = setOf(ProcGroup.FOLLOWUP)),
    CATHETER(AppR.string.pt_catheter, ProcGroup.PROCEDURE, choiceKey = "CATHETER_TYPE"),
    SUTURE(AppR.string.pt_suture, ProcGroup.PROCEDURE, perLimb = true, choiceKey = "SUTURE_TYPE", imageCat = "CLINICAL"),

    /** Section 6. Moved here from section 5; older rows follow it automatically. */
    CONSULT(AppR.string.pt_consult, ProcGroup.CONSULT, choiceKey = "CONSULT_SPECIALTY", imageCat = "CONSULT"),

    /** Section 7: supplies and medical needs (blood and plasma included; units go in the details). */
    IMPLANT(AppR.string.pt_implant, ProcGroup.SUPPLY, perLimb = true, choiceKey = "IMPLANT_TYPE", imageCat = "REPORT"),
    ORTHOSIS(AppR.string.pt_orthosis, ProcGroup.SUPPLY, perLimb = true, choiceKey = "ORTHOSIS_TYPE", imageCat = "REPORT"),
    MOBILITY(AppR.string.pt_mobility, ProcGroup.SUPPLY, choiceKey = "MOBILITY_AID", imageCat = "REPORT"),
    DRESSING(AppR.string.pt_dressing, ProcGroup.SUPPLY, choiceKey = "DRESSING_TYPE", imageCat = "REPORT"),
    BLOOD_UNITS(AppR.string.ord_blood, ProcGroup.SUPPLY, imageCat = "REPORT"),
    PLASMA_UNITS(AppR.string.ord_plasma, ProcGroup.SUPPLY, imageCat = "REPORT"),
    SUPPLY_OTHER(AppR.string.pt_supply_other, ProcGroup.SUPPLY, imageCat = "REPORT"),

    /** Section 8. */
    OUTCOME(AppR.string.pt_outcome, ProcGroup.FOLLOWUP, perLimb = true, choiceKey = "SURGICAL_OUTCOME", imageCat = "XRAY"),
    WOUND_STATUS(AppR.string.pt_wound, ProcGroup.FOLLOWUP, perLimb = true, choiceKey = "WOUND_STATE", imageCat = "CLINICAL"),
    UNION_STATUS(AppR.string.pt_union, ProcGroup.FOLLOWUP, perLimb = true, choiceKey = "UNION_STATE", imageCat = "XRAY"),
    FUNCTION_ROM(AppR.string.pt_rom, ProcGroup.FOLLOWUP, perLimb = true),
    REMOVAL(AppR.string.pt_removal, ProcGroup.FOLLOWUP, perLimb = true, choiceKey = "REMOVAL_ITEM");

    fun offeredIn(g: ProcGroup): Boolean = group == g || g in extraGroups
}

/** The section a row is shown in: the one it was created in, else the default section of its type. */
fun Procedure.sectionOf(): ProcGroup =
    enumOrNull<ProcGroup>(section) ?: enumOrNull<ProcType>(type)?.group ?: ProcGroup.PROCEDURE

/** Ready-made options of every smart dropdown, keyed by the dropdown key. User additions live in ProcOption. */
val ProcPresets: Map<String, List<Int>> = mapOf(
    "REDUCTION_METHOD" to listOf(AppR.string.rd_local, AppR.string.rd_sedation, AppR.string.rd_general, AppR.string.rd_none),
    "SPLINT_TYPE" to listOf(
        AppR.string.sp_back_splint, AppR.string.sp_cast, AppR.string.sp_above_knee, AppR.string.sp_below_knee,
        AppR.string.sp_u_slab, AppR.string.sp_thomas, AppR.string.sp_sling, AppR.string.sp_exfix
    ),
    "SPLINT_POSITION" to listOf(
        AppR.string.spp_neutral, AppR.string.spp_elbow90, AppR.string.spp_wrist_ext, AppR.string.spp_knee_ext,
        AppR.string.spp_ankle90, AppR.string.sp_pillow
    ),
    "GROSS_FINDING" to listOf(
        AppR.string.gr_normal, AppR.string.cl_swelling, AppR.string.cl_deformity, AppR.string.cl_abrasion,
        AppR.string.cl_bruise, AppR.string.cl_wound
    ),
    "PALPATION_FINDING" to listOf(
        AppR.string.pl_normal, AppR.string.cl_tender, AppR.string.pl_crepitus, AppR.string.pl_abn_mobility,
        AppR.string.pl_warmth, AppR.string.cl_compartment
    ),
    "MANEUVER" to listOf(
        AppR.string.mn_lachman, AppR.string.mn_ant_drawer, AppR.string.mn_post_drawer, AppR.string.mn_mcmurray,
        AppR.string.mn_valgus, AppR.string.mn_varus, AppR.string.mn_thompson, AppR.string.mn_tinel, AppR.string.mn_apprehension
    ),
    "NEURO_STATE" to listOf(AppR.string.ne_normal, AppR.string.ne_abnormal, AppR.string.ne_absent, AppR.string.ne_uncoop, AppR.string.ne_paralysis),
    "VASCULAR_STATE" to listOf(AppR.string.va_normal, AppR.string.va_moderate, AppR.string.va_weak, AppR.string.va_not_palpable, AppR.string.va_absent),
    "CATHETER_TYPE" to listOf(AppR.string.ct_foley14, AppR.string.ct_foley16, AppR.string.ct_foley18, AppR.string.ct_failed),
    "SUTURE_TYPE" to listOf(AppR.string.su_simple, AppR.string.su_subcut, AppR.string.su_debride, AppR.string.su_staples),
    "CONSULT_SPECIALTY" to listOf(
        AppR.string.cs_general, AppR.string.cs_neuro, AppR.string.cs_internal, AppR.string.cs_anesthesia,
        AppR.string.cs_cardiology, AppR.string.cs_vascular, AppR.string.cs_plastic
    ),
    "LAB_PANEL" to listOf(AppR.string.lb_cbc, AppR.string.lb_chem, AppR.string.lb_coag, AppR.string.lb_cross, AppR.string.lb_group, AppR.string.lb_urine),
    "IMPLANT_TYPE" to listOf(
        AppR.string.imp_plate, AppR.string.imp_screws, AppR.string.imp_im_nail, AppR.string.imp_kwires,
        AppR.string.sp_exfix, AppR.string.imp_prosthesis
    ),
    "ORTHOSIS_TYPE" to listOf(AppR.string.or_brace, AppR.string.or_boot, AppR.string.or_collar, AppR.string.sp_sling, AppR.string.or_knee),
    "MOBILITY_AID" to listOf(AppR.string.mb_crutches, AppR.string.mb_walker, AppR.string.mb_wheelchair, AppR.string.mb_cane),
    "DRESSING_TYPE" to listOf(AppR.string.ds_dressing, AppR.string.ds_bandage, AppR.string.ds_vac, AppR.string.ds_sutures),
    "SURGICAL_OUTCOME" to listOf(AppR.string.oc_excellent, AppR.string.oc_good, AppR.string.oc_fair, AppR.string.oc_complication, AppR.string.oc_reop),
    "WOUND_STATE" to listOf(AppR.string.wd_healed, AppR.string.wd_clean, AppR.string.wd_discharge, AppR.string.wd_infection, AppR.string.wd_dehiscence),
    "UNION_STATE" to listOf(AppR.string.un_united, AppR.string.un_progress, AppR.string.un_delayed, AppR.string.un_nonunion, AppR.string.un_malunion),
    "REMOVAL_ITEM" to listOf(AppR.string.rm_stitches, AppR.string.rm_cast, AppR.string.rm_kwires, AppR.string.rm_drain, AppR.string.rm_exfix, AppR.string.rm_implant)
)
