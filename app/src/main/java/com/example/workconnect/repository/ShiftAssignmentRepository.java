package com.example.workconnect.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.workconnect.models.ShiftAssignment;
import com.example.workconnect.models.ShiftTemplate;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class ShiftAssignmentRepository {

    public interface SimpleCallback {
        void onComplete(boolean success, String message);
    }

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public LiveData<List<ShiftAssignment>> listenAssignmentsForDate(String companyId, String teamId, String dateKey) {
        MutableLiveData<List<ShiftAssignment>> live = new MutableLiveData<>(new ArrayList<>());

        db.collection("companies").document(companyId)
                .collection("teams").document(teamId)
                .collection("assignments").document(dateKey)
                .collection("items") // NOTE: "items" = shift assignments for this date (may rename later)

                .addSnapshotListener((snap, e) -> {
                    if (e != null || snap == null) {
                        live.postValue(new ArrayList<>());
                        return;
                    }

                    List<ShiftAssignment> list = new ArrayList<>();
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        ShiftAssignment a = doc.toObject(ShiftAssignment.class);
                        if (a != null) {
                            // doc id is the userId (but keep it anyway)
                            a.setId(doc.getId());
                            if (a.getUserId() == null || a.getUserId().trim().isEmpty()) {
                                a.setUserId(doc.getId());
                            }
                            list.add(a);
                        }
                    }
                    live.postValue(list);
                });

        return live;
    }

    /**
     * Enforces: ONE shift per user per day.
     *
     * - For each selected user: we set docId=userId to this template => moves them if they had another template.
     * - For users previously on THIS template but now unselected: delete their doc.
     */
    public void setAssignmentsForTemplate(
            String companyId,
            String teamId,
            String dateKey,
            ShiftTemplate template,
            List<String> selectedUserIds,
            SimpleCallback cb
    ) {
        if (template == null || template.getId() == null) {
            cb.onComplete(false, "Template is missing");
            return;
        }

        var itemsCol = db.collection("companies").document(companyId)
                .collection("teams").document(teamId)
                .collection("assignments").document(dateKey)
                .collection("items");

        itemsCol.get().addOnSuccessListener(allSnap -> {
            var batch = db.batch();

            // Build map userId -> existing assignment
            HashMap<String, ShiftAssignment> existing = new HashMap<>();
            for (DocumentSnapshot doc : allSnap.getDocuments()) {
                ShiftAssignment a = doc.toObject(ShiftAssignment.class);
                if (a != null) {
                    String uid = a.getUserId();
                    if (uid == null || uid.trim().isEmpty()) uid = doc.getId();
                    a.setUserId(uid);
                    existing.put(uid, a);
                }
            }

            // Normalize selected list
            List<String> selected = new ArrayList<>();
            if (selectedUserIds != null) {
                for (String uid : selectedUserIds) {
                    if (uid != null && !uid.trim().isEmpty()) selected.add(uid.trim());
                }
            }

            // 1) Delete users that were on THIS template but are now unselected
            for (String uid : existing.keySet()) {
                ShiftAssignment a = existing.get(uid);
                if (a == null) continue;

                boolean wasOnThisTemplate = template.getId().equals(a.getTemplateId());
                boolean stillSelected = selected.contains(uid);

                if (wasOnThisTemplate && !stillSelected) {
                    DocumentReference ref = itemsCol.document(uid);
                    batch.delete(ref);
                }
            }

            // 2) Upsert selected users to THIS template (moves them automatically)
            for (String uid : selected) {
                DocumentReference ref = itemsCol.document(uid);

                HashMap<String, Object> data = new HashMap<>();
                data.put("userId", uid);
                data.put("templateId", template.getId());
                data.put("templateTitle", template.getTitle() == null ? "" : template.getTitle());
                data.put("startHour", template.getStartHour());
                data.put("endHour", template.getEndHour());
                data.put("createdAt", FieldValue.serverTimestamp());

                batch.set(ref, data);
            }

            batch.commit().addOnCompleteListener(t ->
                    cb.onComplete(t.isSuccessful(), t.isSuccessful() ? "Saved" : "Failed to save")
            );
        }).addOnFailureListener(e -> cb.onComplete(false, e.getMessage() == null ? "Failed to load assignments" : e.getMessage()));
    }
}
