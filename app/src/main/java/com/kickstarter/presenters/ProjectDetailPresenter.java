package com.kickstarter.presenters;

import com.kickstarter.models.Project;
import com.kickstarter.services.KickstarterClient;
import com.kickstarter.ui.activities.ProjectDetailActivity;

import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.subjects.PublishSubject;

public class ProjectDetailPresenter {
  private ProjectDetailActivity view;
  private final PublishSubject<Void> viewTaken;

  public ProjectDetailPresenter(final Project project) {
    KickstarterClient client = new KickstarterClient(); // TODO: Inject
    this.viewTaken = PublishSubject.create();

    Observable.combineLatest(
      Observable.just(project).mergeWith(client.fetchProject(project.id())),
      viewTaken,
      (p, n) -> p
    )
    .observeOn(AndroidSchedulers.mainThread())
    .subscribe(p -> {
      if (view != null) {
        view.show(p);
      }
    });
  }

  public void onTakeView(ProjectDetailActivity view) {
    this.view = view;
    viewTaken.onNext(null);
  }
}
