package com.example.workconnect.repository;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.workconnect.models.ShiftAssignment;
import com.example.workconnect.models.ShiftSwapOffer;
import com.example.workconnect.models.ShiftSwapRequest;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class ShiftSwapRepository {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    private DocumentReference reqDoc(String companyId, String teamId, String requestId) {
        return db.collection("companies").document(companyId)
                .collection("teams").document(teamId)
                .collection("swapRequests").document(requestId);
    }

    public interface SimpleCallback {
        void onDone(boolean success, String msg);
    }

    // -----------------------
    // LISTENERS (FIXED)
    // -----------------------

    public LiveData<List<ShiftSwapRequest>> listenMyRequests(String companyId, String teamId, String myUid) {
        MutableLiveData<List<ShiftSwapRequest>> live = new MutableLiveData<>(new ArrayList<>());

        db.collection("companies").document(companyId)
                .collection("teams").document(teamId)
                .collection("swapRequests")
                .whereEqualTo("requesterUid", myUid)
                .addSnapshotListener((snap, e) -> {
                    if (e != null || snap == null) {
                        return;
                    }

                    List<ShiftSwapRequest> out = new ArrayList<>();
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        ShiftSwapRequest r = d.toObject(ShiftSwapRequest.class);
                        if (r != null) {
                            r.setId(d.getId());
                            out.add(r);
                        }
                    }

                    Collections.sort(out, (a, b) -> Long.compare(b.getCreatedAt(), a.getCreatedAt()));
                    live.postValue(out);
                });

        return live;
    }

    public LiveData<List<ShiftSwapRequest>> listenOpenRequests(String companyId, String teamId, String myUid) {
        MutableLiveData<List<ShiftSwapRequest>> live = new MutableLiveData<>(new ArrayList<>());

        db.collection("companies").document(companyId)
                .collection("teams").document(teamId)
                .collection("swapRequests")
                .whereEqualTo("status", ShiftSwapRequest.OPEN)
                .addSnapshotListener((snap, e) -> {
                    if (e != null || snap == null) {
                        return;
                    }

                    List<ShiftSwapRequest> out = new ArrayList<>();
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        ShiftSwapRequest r = d.toObject(ShiftSwapRequest.class);
                        if (r == null) continue;

                        r.setId(d.getId());

                        if (myUid != null && myUid.equals(r.getRequesterUid())) continue;

                        out.add(r);
                    }

                    Collections.sort(out, (a, b) -> Long.compare(b.getCreatedAt(), a.getCreatedAt()));
                    live.postValue(out);
                });

        return live;
    }

    public LiveData<List<ShiftSwapRequest>> listenPendingApprovals(String companyId, String teamId) {
        MutableLiveData<List<ShiftSwapRequest>> live = new MutableLiveData<>(new ArrayList<>());

        db.collection("companies").document(companyId)
                .collection("teams").document(teamId)
                .collection("swapRequests")
                .whereEqualTo("status", ShiftSwapRequest.PENDING_APPROVAL)
                .addSnapshotListener((snap, e) -> {
                    if (e != null || snap == null) {
                        return;
                    }

                    List<ShiftSwapRequest> out = new ArrayList<>();
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        ShiftSwapRequest r = d.toObject(ShiftSwapRequest.class);
                        if (r != null) {
                            r.setId(d.getId());
                            out.add(r);
                        }
                    }

                    Collections.sort(out, (a, b) -> Long.compare(b.getCreatedAt(), a.getCreatedAt()));
                    live.postValue(out);
                });

        return live;
    }

    public LiveData<List<ShiftSwapOffer>> listenOffers(String companyId, String teamId, String requestId) {
        MutableLiveData<List<ShiftSwapOffer>> live = new MutableLiveData<>(new ArrayList<>());

        reqDoc(companyId, teamId, requestId)
                .collection("offers")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .addSnapshotListener((snap, e) -> {
                    if (e != null || snap == null) {
                        return;
                    }

                    List<ShiftSwapOffer> out = new ArrayList<>();
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        ShiftSwapOffer o = d.toObject(ShiftSwapOffer.class);
                        if (o != null) {
                            o.setId(d.getId());
                            out.add(o);
                        }
                    }
                    live.postValue(out);
                });

        return live;
    }

    // -----------------------
    // MUTATIONS
    // -----------------------

    public void createRequest(String companyId, String teamId, ShiftSwapRequest r, SimpleCallback cb) {
        if (r == null) { cb.onDone(false, "Missing request"); return; }

        DocumentReference doc = db.collection("companies").document(companyId)
                .collection("teams").document(teamId)
                .collection("swapRequests").document();

        r.setId(doc.getId());
        r.setCompanyId(companyId);
        r.setTeamId(teamId);
        r.setStatus(ShiftSwapRequest.OPEN);
        r.setSelectedOfferId(null);
        r.setCreatedAt(System.currentTimeMillis());

        doc.set(r)
                .addOnSuccessListener(unused -> cb.onDone(true, "Request created"))
                .addOnFailureListener(e -> cb.onDone(false, "Failed: " + (e.getMessage() == null ? "" : e.getMessage())));
    }

    public void cancelRequest(String companyId, String teamId, String requestId, SimpleCallback cb) {
        reqDoc(companyId, teamId, requestId)
                .update("status", ShiftSwapRequest.CANCELLED)
                .addOnSuccessListener(unused -> cb.onDone(true, "Cancelled"))
                .addOnFailureListener(e -> cb.onDone(false, "Failed: " + (e.getMessage() == null ? "" : e.getMessage())));
    }

    public void expireRequest(String companyId, String teamId, String requestId) {
        reqDoc(companyId, teamId, requestId).update("status", ShiftSwapRequest.EXPIRED);
    }

    public void makeOffer(String companyId, String teamId, String requestId, ShiftSwapOffer o, SimpleCallback cb) {
        if (o == null) { cb.onDone(false, "Missing offer"); return; }

        DocumentReference doc = reqDoc(companyId, teamId, requestId)
                .collection("offers").document();

        o.setId(doc.getId());
        o.setRequestId(requestId);
        o.setCreatedAt(System.currentTimeMillis());

        doc.set(o)
                .addOnSuccessListener(unused -> cb.onDone(true, "Offer submitted"))
                .addOnFailureListener(e -> cb.onDone(false, "Failed: " + (e.getMessage() == null ? "" : e.getMessage())));
    }

    public void withdrawOffer(String companyId, String teamId, String requestId, String offerId, SimpleCallback cb) {
        reqDoc(companyId, teamId, requestId)
                .collection("offers").document(offerId)
                .delete()
                .addOnSuccessListener(unused -> cb.onDone(true, "Offer withdrawn"))
                .addOnFailureListener(e -> cb.onDone(false, "Failed: " + (e.getMessage() == null ? "" : e.getMessage())));
    }

    public void submitForApproval(String companyId, String teamId, String requestId, String offerId, SimpleCallback cb) {
        HashMap<String, Object> up = new HashMap<>();
        up.put("status", ShiftSwapRequest.PENDING_APPROVAL);
        up.put("selectedOfferId", offerId);

        reqDoc(companyId, teamId, requestId)
                .update(up)
                .addOnSuccessListener(unused -> cb.onDone(true, "Sent for manager approval"))
                .addOnFailureListener(e -> cb.onDone(false, "Failed: " + (e.getMessage() == null ? "" : e.getMessage())));
    }

    public void managerRejectPending(String companyId, String teamId, String requestId, SimpleCallback cb) {
        HashMap<String, Object> up = new HashMap<>();
        up.put("status", ShiftSwapRequest.OPEN);
        up.put("selectedOfferId", null);

        reqDoc(companyId, teamId, requestId)
                .update(up)
                .addOnSuccessListener(unused -> cb.onDone(true, "Rejected (back to open)"))
                .addOnFailureListener(e -> cb.onDone(false, "Failed: " + (e.getMessage() == null ? "" : e.getMessage())));
    }

    public void managerMarkApproved(String companyId, String teamId, String requestId, SimpleCallback cb) {
        reqDoc(companyId, teamId, requestId)
                .update("status", ShiftSwapRequest.APPROVED)
                .addOnSuccessListener(unused -> cb.onDone(true, "Approved"))
                .addOnFailureListener(e -> cb.onDone(false, "Failed: " + (e.getMessage() == null ? "" : e.getMessage())));
    }

    // ===========================
    // Availability check (NEW)
    // ===========================
    public void hasMyShiftOnDate(
            @NonNull String companyId,
            @NonNull String teamId,
            @NonNull String dateKey,
            @NonNull String userUid,
            @NonNull SimpleCallback cb
    ) {
        db.collection("companies").document(companyId)
                .collection("teams").document(teamId)
                .collection("assignments").document(dateKey)
                .collection("items").document(userUid)
                .get()
                .addOnSuccessListener(doc -> {
                    boolean has = (doc != null && doc.exists());
                    cb.onDone(true, has ? "HAS_SHIFT" : "FREE");
                })
                .addOnFailureListener(e -> cb.onDone(false, "Failed: " + (e.getMessage() == null ? "" : e.getMessage())));
    }

    // ===========================
    // Upcoming shifts picker
    // ===========================
    public static class UpcomingShift {
        public String id;
        public String dateKey;
        public String templateId;
        public String templateTitle;

        public UpcomingShift() {}

        public UpcomingShift(String dateKey, String templateId, String templateTitle, String id) {
            this.dateKey = dateKey;
            this.templateId = templateId;
            this.templateTitle = templateTitle;
            this.id = id;
        }

        public String getDateKey() { return dateKey; }
    }

    public LiveData<List<UpcomingShift>> listenMyUpcomingShifts(
            @NonNull String companyId,
            @NonNull List<String> teamIds,
            @NonNull String userUid
    ) {
        MutableLiveData<List<UpcomingShift>> live = new MutableLiveData<>(new ArrayList<>());

        if (teamIds.isEmpty()) {
            live.postValue(new ArrayList<>());
            return live;
        }

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        String todayKey = String.format("%04d-%02d-%02d",
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH));

        Map<String, UpcomingShift> bucket = new HashMap<>();
        AtomicInteger pendingTeams = new AtomicInteger(teamIds.size());

        for (String teamId : teamIds) {
            db.collection("companies").document(companyId)
                    .collection("teams").document(teamId)
                    .collection("assignments")
                    .orderBy(FieldPath.documentId())
                    .startAfter(todayKey)
                    .get()
                    .addOnSuccessListener(assignSnap -> {
                        List<DocumentSnapshot> assignDocs = assignSnap.getDocuments();
                        if (assignDocs.isEmpty()) {
                            if (pendingTeams.decrementAndGet() == 0) {
                                List<UpcomingShift> list = new ArrayList<>(bucket.values());
                                list.sort((a, b) -> a.dateKey.compareTo(b.dateKey));
                                live.postValue(list);
                            }
                            return;
                        }

                        AtomicInteger pendingDates = new AtomicInteger(assignDocs.size());

                        for (DocumentSnapshot assignDoc : assignDocs) {
                            String dateKey = assignDoc.getId();

                            assignDoc.getReference()
                                    .collection("items")
                                    .whereEqualTo("userId", userUid)
                                    .get()
                                    .addOnSuccessListener(itemsSnap -> {
                                        for (DocumentSnapshot itemDoc : itemsSnap.getDocuments()) {
                                            ShiftAssignment a = itemDoc.toObject(ShiftAssignment.class);
                                            if (a == null) continue;

                                            UpcomingShift us = new UpcomingShift(
                                                    dateKey,
                                                    a.getTemplateId(),
                                                    a.getTemplateTitle(),
                                                    itemDoc.getId()
                                            );

                                            String key = teamId + "|" + dateKey + "|" + a.getTemplateId();
                                            bucket.put(key, us);
                                        }

                                        if (pendingDates.decrementAndGet() == 0) {
                                            if (pendingTeams.decrementAndGet() == 0) {
                                                List<UpcomingShift> list = new ArrayList<>(bucket.values());
                                                list.sort((u1, u2) -> u1.dateKey.compareTo(u2.dateKey));
                                                live.postValue(list);
                                            }
                                        }
                                    })
                                    .addOnFailureListener(e -> {
                                        if (pendingDates.decrementAndGet() == 0) {
                                            if (pendingTeams.decrementAndGet() == 0) {
                                                List<UpcomingShift> list = new ArrayList<>(bucket.values());
                                                list.sort((u1, u2) -> u1.dateKey.compareTo(u2.dateKey));
                                                live.postValue(list);
                                            }
                                        }
                                    });
                        }
                    })
                    .addOnFailureListener(e -> {
                        if (pendingTeams.decrementAndGet() == 0) {
                            List<UpcomingShift> list = new ArrayList<>(bucket.values());
                            list.sort((a, b) -> a.dateKey.compareTo(b.dateKey));
                            live.postValue(list);
                        }
                    });
        }

        return live;
    }
}
