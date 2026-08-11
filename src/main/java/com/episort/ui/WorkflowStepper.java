package com.episort.ui;

import java.util.ArrayList;
import java.util.List;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

/**
 * The numbered workflow strip — folder, analysis, TMDB, plan review, apply.
 *
 * <p>Lives outside the scan screen because the exact-plan review shows the very
 * same strip: the plan replaces the scan screen in the content area, and without
 * the strip following the user there, the review reads as a different place
 * rather than the next step of the one they were on.
 */
public final class WorkflowStepper {
    private final HBox root = new HBox(0);
    private final List<Step> steps = new ArrayList<>();
    private WorkflowPhase phase = WorkflowPhase.CHOOSE_FOLDER;
    private boolean inProgress;

    public WorkflowStepper(AppLanguage language) {
        root.getStyleClass().add("workflow-steps");
        root.setAlignment(Pos.CENTER_LEFT);
        applyLanguage(language);
    }

    public Region root() {
        return root;
    }

    public void applyLanguage(AppLanguage language) {
        root.getChildren().clear();
        steps.clear();
        String[] labels = UiText.scanWorkflowSteps(language);
        for (int index = 0; index < labels.length; index++) {
            Step step = new Step(index + 1, labels[index]);
            steps.add(step);
            root.getChildren().add(step.root);
            if (index < labels.length - 1) {
                Region connector = new Region();
                connector.getStyleClass().add("workflow-step-connector");
                HBox.setHgrow(connector, Priority.ALWAYS);
                root.getChildren().add(connector);
            }
        }
        refresh();
    }

    public void setPhase(WorkflowPhase phase, boolean inProgress) {
        if (phase == null) {
            return;
        }
        this.phase = phase;
        this.inProgress = inProgress;
        refresh();
    }

    public WorkflowPhase phase() {
        return phase;
    }

    private void refresh() {
        int activeIndex = phase.index();
        for (int index = 0; index < steps.size(); index++) {
            steps.get(index).setState(index, activeIndex, inProgress && index == activeIndex);
        }
    }

    private static final class Step {
        private final HBox root;
        private final Label number;
        private final ProgressIndicator loader;
        private final Label label;

        Step(int stepNumber, String text) {
            number = new Label(String.valueOf(stepNumber));
            number.getStyleClass().add("workflow-step-number");
            loader = new ProgressIndicator();
            loader.getStyleClass().add("workflow-step-loader");
            loader.setMaxSize(22, 22);
            loader.setVisible(false);
            loader.setManaged(false);
            label = new Label(text);
            label.getStyleClass().add("workflow-step-label");
            root = new HBox(8, number, loader, label);
            root.getStyleClass().add("workflow-step");
            root.setAlignment(Pos.CENTER_LEFT);
        }

        void setState(int index, int activeIndex, boolean loading) {
            root.getStyleClass().setAll("workflow-step");
            number.setVisible(!loading);
            number.setManaged(!loading);
            loader.setVisible(loading);
            loader.setManaged(loading);
            if (index == activeIndex) {
                root.getStyleClass().add("active");
                if (loading) {
                    root.getStyleClass().add("loading");
                }
            } else if (index < activeIndex) {
                root.getStyleClass().add("complete");
            } else {
                root.getStyleClass().add("pending");
            }
        }
    }
}
