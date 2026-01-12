package com.example.workconnect.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.annotation.NonNull;

import com.example.workconnect.models.ShiftAssignment;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldPath;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.example.workconnect.models.ShiftSwapOffer;
import com.example.workconnect.models.ShiftSwapRequest;
import java.util.ArrayList;
import java.util.List;
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

    public LiveData<List<ShiftSwapRequest>> listenMyRequests(String companyId, String teamId, String myUid) {
        MutableLiveData<List<ShiftSwapRequest>> live = new MutableLiveData<>(new ArrayList<>());

        db.collection("companies").document(companyId)
                .collection("teams").document(teamId)
                .collection("swapRequests")
                .whereEqualTo("requesterUid", myUid)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener((snap, e) -> {
                    List<ShiftSwapRequest> out = new ArrayList<>();
                    if (snap != null) {
                        for (var d : snap.getDocuments()) {
                            ShiftSwapRequest r = d.toObject(ShiftSwapRequest.class);
                            if (r != null) {
                                r.setId(d.getId());
                                out.add(r);
                            }
                        }
                    }
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
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener((snap, e) -> {
                    List<ShiftSwapRequest> out = new ArrayList<>();
                    if (snap != null) {
                        for (var d : snap.getDocuments()) {
                            ShiftSwapRequest r = d.toObject(ShiftSwapRequest.class);
                            if (r == null) continue;
                            r.setId(d.getId());
                            if (myUid != null && myUid.equals(r.getRequesterUid())) continue;
                            out.add(r);
                        }
                    }
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
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener((snap, e) -> {
                    List<ShiftSwapRequest> out = new ArrayList<>();
                    if (snap != null) {
                        for (var d : snap.getDocuments()) {
                            ShiftSwapRequest r = d.toObject(ShiftSwapRequest.class);
                            if (r != null) {
                                r.setId(d.getId());
                                out.add(r);
                            }
                        }
                    }
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
                    List<ShiftSwapOffer> out = new ArrayList<>();
                    if (snap != null) {
                        for (var d : snap.getDocuments()) {
                            ShiftSwapOffer o = d.toObject(ShiftSwapOffer.class);
                            if (o != null) {
                                o.setId(d.getId());
                                out.add(o);
                            }
                        }
                    }
                    live.postValue(out);
                });

        return live;
    }

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
    // NEW: Upcoming shifts picker
    // ===========================

    // ===========================
    // Upcoming shifts picker
    // ===========================
    public static class UpcomingShift {
        public String id;          // item doc id
        public String dateKey;      // YYYY-MM-DD (assignments doc id)
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

    /**
     * Reads upcoming shifts from:
     * companies/{companyId}/teams/{teamId}/assignments/{dateKey}/items/{itemId}
     * where item has: userId, templateId, templateTitle
     */
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

        // todayKey = YYYY-MM-DD
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        String todayKey = String.format("%04d-%02d-%02d",
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH));

        // bucket to dedupe: teamId|dateKey|templateId
        Map<String, UpcomingShift> bucket = new HashMap<>();

        AtomicInteger pendingTeams = new AtomicInteger(teamIds.size());

        for (String teamId : teamIds) {
            db.collection("companies").document(companyId)
                    .collection("teams").document(teamId)
                    .collection("assignments")
                    // IMPORTANT: dateKey is the document id, so query by documentId + orderBy documentId
                    .orderBy(FieldPath.documentId())
                    .startAfter(todayKey) // strictly future (so 14th is included when today is 12th)
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

                                            String templateId = a.getTemplateId();
                                            String templateTitle = a.getTemplateTitle();

                                            UpcomingShift us = new UpcomingShift(
                                                    dateKey,
                                                    templateId,
                                                    templateTitle,
                                                    itemDoc.getId()
                                            );

                                            String key = teamId + "|" + dateKey + "|" + templateId;
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
