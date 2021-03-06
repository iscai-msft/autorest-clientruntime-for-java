/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */
package com.microsoft.azure.arm.resources.models.implementation;

import com.microsoft.azure.arm.resources.models.ExternalChildResource;
import com.microsoft.azure.arm.dag.FunctionalTaskItem;
import com.microsoft.azure.arm.dag.IndexableTaskItem;
import com.microsoft.azure.arm.dag.TaskGroup;
import com.microsoft.azure.arm.model.Appliable;
import com.microsoft.azure.arm.model.Creatable;
import com.microsoft.azure.arm.model.Executable;
import com.microsoft.azure.arm.model.Indexable;
import com.microsoft.azure.arm.model.Refreshable;
import com.microsoft.azure.arm.utils.Utils;
import com.microsoft.rest.ServiceCallback;
import com.microsoft.rest.ServiceFuture;
import rx.Completable;
import rx.Observable;
import rx.functions.Action1;
import rx.functions.Func1;

import java.util.Objects;

/**
 * Externalized child resource abstract implementation.
 *
 * Inorder to be eligible for an external child resource following criteria must be satisfied:
 * 1. It's is always associated with a parent resource and has no existence without parent
 *    i.e. if you delete parent then child resource will be deleted automatically.
 * 2. Parent may or may not contain collection of child resources (i.e. as inline collection property).
 * 3. It's has an ID and can be created, updated, fetched and deleted independent of the parent
 *    i.e. CRUD on child resource does not require CRUD on the parent
 * (Internal use only)
 *
 * @param <FluentModelT> the fluent model type of the child resource
 * @param <InnerModelT> Azure inner resource class type representing the child resource
 * @param <ParentImplT> the parent Azure resource impl class type that implements {@link ParentT}
 * @param <ParentT> parent interface
 */
public abstract class ExternalChildResourceImpl<FluentModelT extends Indexable,
        InnerModelT,
        ParentImplT extends ParentT,
        ParentT>
        extends
        ChildResourceImpl<InnerModelT, ParentImplT, ParentT>
        implements
        Appliable<FluentModelT>,
        Creatable<FluentModelT>,
            TaskGroup.HasTaskGroup,
        ExternalChildResource<FluentModelT, ParentT>,
        Refreshable<FluentModelT> {
    /**
     * State representing any pending action that needs to be performed on this child resource.
     */
    private PendingOperation pendingOperation = PendingOperation.None;
    /**
     * The child resource name.
     */
    private final String name;
    /**
     * TaskItem in the graph to perform action on this external child resource.
     */
    private final ExternalChildActionTaskItem childAction;

    /**
     * Creates an instance of external child resource in-memory.
     *
     * @param name the name of this external child resource
     * @param parent reference to the parent of this external child resource
     * @param innerObject reference to the inner object representing this external child resource
     */
    protected ExternalChildResourceImpl(String name,
                                        ParentImplT parent,
                                        InnerModelT innerObject) {
        super(innerObject, parent);
        this.childAction = new ExternalChildActionTaskItem(this);
        this.name = name;
    }

    /**
     * Creates an instance of external child resource in-memory.
     *
     * @param key the task group key for the task item that perform actions on this child
     * @param name the name of this external child resource
     * @param parent reference to the parent of this external child resource
     * @param innerObject reference to the inner object representing this external child resource
     */
    protected ExternalChildResourceImpl(String key,
                                        String name,
                                        ParentImplT parent,
                                        InnerModelT innerObject) {
        super(innerObject, parent);
        this.childAction = new ExternalChildActionTaskItem(key, this);
        this.name = name;
    }

    @Override
    public String name() {
        return this.name;
    }

    /**
     * @return the operation pending on this child resource.
     */
    public PendingOperation pendingOperation() {
        return this.pendingOperation;
    }

    /**
     * Update the operation state.
     *
     * @param pendingOperation the new state of this child resource
     */
    public void setPendingOperation(PendingOperation pendingOperation) {
        this.pendingOperation = pendingOperation;
    }

    /**
     * Mark that there is no action pending on this child resource and clear
     * any cached result, i.e. the output produced by the invocation of last
     * action.
     */
    public void clear() {
        this.setPendingOperation(PendingOperation.None);
        this.childAction.clear();
    }

    /**
     * @return the task group associated with this external child resource.
     */
    @Override
    public TaskGroup taskGroup() {
        return this.childAction.taskGroup();
    }

    /**
     * Prepare this external child resource for update.
     *
     * @return this external child resource prepared for update
     */
    @SuppressWarnings("unchecked")
    protected final FluentModelT prepareUpdate() {
        this.setPendingOperation(PendingOperation.ToBeCreated);
        return (FluentModelT) this;
    }

    /**
     * Creates this external child resource.
     *
     * @return the observable to track the create action
     */
    public abstract Observable<FluentModelT> createResourceAsync();

    /**
     * Update this external child resource.
     *
     * @return the observable to track the update action
     */
    public abstract Observable<FluentModelT> updateResourceAsync();

    /**
     * Delete this external child resource.
     *
     * @return the observable to track the delete action.
     */
    public abstract Observable<Void> deleteResourceAsync();

    /**
     * @return the key of this child resource in the collection maintained by ExternalChildResourceCollectionImpl
     */
    public String childResourceKey() {
        return name();
    }

    @Override
    public final FluentModelT refresh() {
        return refreshAsync().toBlocking().last();
    }

    @Override
    public Observable<FluentModelT> refreshAsync() {
        final ExternalChildResourceImpl<FluentModelT, InnerModelT, ParentImplT, ParentT> self = this;
        return this.getInnerAsync().map(new Func1<InnerModelT, FluentModelT>() {
            @Override
            public FluentModelT call(InnerModelT innerModelT) {
                self.setInner(innerModelT);
                return (FluentModelT) self;
            }
        });
    }

    /**
     * Add a dependency task item for this model.
     *
     * @param dependency the dependency task item.
     * @return key to be used as parameter to taskResult(string) method to retrieve result the task item
     */
    protected String addDependency(FunctionalTaskItem dependency) {
        Objects.requireNonNull(dependency);
        return this.taskGroup().addDependency(dependency);
    }

    /**
     * Add a dependency task group for this model.
     *
     * @param dependency the dependency.
     * @return key to be used as parameter to taskResult(string) method to retrieve result of root
     * task in the given dependency task group
     */
    protected String addDependency(TaskGroup.HasTaskGroup dependency) {
        Objects.requireNonNull(dependency);
        this.taskGroup().addDependencyTaskGroup(dependency.taskGroup());
        return dependency.taskGroup().key();
    }

    /**
     * Add a creatable dependency for this model.
     *
     * @param creatable the creatable dependency.
     * @return the key to be used as parameter to taskResult(string) method to retrieve created dependency
     */
    @SuppressWarnings("unchecked")
    protected String addDependency(Creatable<? extends Indexable> creatable) {
        TaskGroup.HasTaskGroup dependency = (TaskGroup.HasTaskGroup) creatable;
        return this.addDependency(dependency);
    }

    /**
     * Add an appliable dependency for this model.
     *
     * @param appliable the appliable dependency.
     * @return the key to be used as parameter to taskResult(string) method to retrieve updated dependency
     */
    @SuppressWarnings("unchecked")
    protected String addeDependency(Appliable<? extends Indexable> appliable) {
        TaskGroup.HasTaskGroup dependency = (TaskGroup.HasTaskGroup) appliable;
        return this.addDependency(dependency);
    }

    /**
     * Add an executable dependency for this model.
     *
     * @param executable the executable dependency
     * @return the key to be used as parameter to taskResult(string) method to retrieve result of executing
     * the executable dependency
     */
    @SuppressWarnings("unchecked")
    protected String addDependency(Executable<? extends Indexable> executable) {
        TaskGroup.HasTaskGroup dependency = (TaskGroup.HasTaskGroup) executable;
        return this.addDependency(dependency);
    }

    /**
     * Add a "post-run" dependent task item for this model.
     *
     * @param dependent the "post-run" dependent task item.
     * @return key to be used as parameter to taskResult(string) method to retrieve result of root
     * task in the given dependent task group
     */
    public String addPostRunDependent(FunctionalTaskItem dependent) {
        Objects.requireNonNull(dependent);
        return this.taskGroup().addPostRunDependent(dependent);
    }

    /**
     * Add a "post-run" dependent for this model.
     *
     * @param dependent the "post-run" dependent.
     * @return key to be used as parameter to taskResult(string) method to retrieve result of root
     * task in the given dependent task group
     */
    protected String addPostRunDependent(TaskGroup.HasTaskGroup dependent) {
        Objects.requireNonNull(dependent);
        this.taskGroup().addPostRunDependentTaskGroup(dependent.taskGroup());
        return dependent.taskGroup().key();
    }

    /**
     * Add a creatable "post-run" dependent for this model.
     *
     * @param creatable the creatable "post-run" dependent.
     * @return the key to be used as parameter to taskResult(string) method to retrieve created "post-run" dependent
     */
    @SuppressWarnings("unchecked")
    protected String addPostRunDependent(Creatable<? extends Indexable> creatable) {
        TaskGroup.HasTaskGroup dependency = (TaskGroup.HasTaskGroup) creatable;
        return this.addPostRunDependent(dependency);
    }

    /**
     * Add an appliable "post-run" dependent for this model.
     *
     * @param appliable the appliable "post-run" dependent.
     * @return the key to be used as parameter to taskResult(string) method to retrieve updated "post-run" dependent
     */
    @SuppressWarnings("unchecked")
    protected String addPostRunDependent(Appliable<? extends Indexable> appliable) {
        TaskGroup.HasTaskGroup dependency = (TaskGroup.HasTaskGroup) appliable;
        return this.addPostRunDependent(dependency);
    }

    /**
     * Add an executable "post-run" dependent for this model.
     *
     * @param executable the executable "post-run" dependent
     * @return the key to be used as parameter to taskResult(string) method to retrieve result of executing
     * the executable "post-run" dependent
     */
    @SuppressWarnings("unchecked")
    protected void addPostRunDependent(Executable<? extends Indexable> executable) {
        TaskGroup.HasTaskGroup dependency = (TaskGroup.HasTaskGroup) executable;
        this.addPostRunDependent(dependency);
    }

    /**
     * Enables adding delayed dependencies and depends.
     */
    public void beforeGroupCreateOrUpdate() {
        // NOP: Extended types can override this to add additional dependencies
        //
    }

    @Override
    public Observable<Indexable> createAsync() {
        return taskGroup().invokeAsync(this.taskGroup().newInvocationContext());
    }

    @Override
    public FluentModelT create() {
        return Utils.<FluentModelT>rootResource(createAsync()).toBlocking().single();
    }

    @Override
    public ServiceFuture<FluentModelT> createAsync(final ServiceCallback<FluentModelT> callback) {
        return ServiceFuture.fromBody(Utils.<FluentModelT>rootResource(createAsync()), callback);
    }

    @Override
    public Observable<FluentModelT> applyAsync() {
        return taskGroup().invokeAsync(this.taskGroup().newInvocationContext())
                .last()
                .map(new Func1<Indexable, FluentModelT>() {
                    @Override
                    public FluentModelT call(Indexable indexable) {
                        return (FluentModelT) indexable;
                    }
                });
    }

    @Override
    public FluentModelT apply() {
        return applyAsync().toBlocking().last();
    }

    @Override
    public ServiceFuture<FluentModelT> applyAsync(ServiceCallback<FluentModelT> callback) {
        return ServiceFuture.fromBody(applyAsync(), callback);
    }

    protected abstract Observable<InnerModelT> getInnerAsync();

    protected Completable afterPostRunAsync(boolean isGroupFaulted) {
        return Completable.complete();
    }

    /**
     * The possible operation pending on a child resource in-memory.
     */
    public enum PendingOperation {
        /**
         * No action needs to be taken on resource.
         */
        None,
        /**
         * Child resource required to be created.
         */
        ToBeCreated,
        /**
         * Child resource required to be updated.
         */
        ToBeUpdated,
        /**
         * Child resource required to be deleted.
         */
        ToBeRemoved
    }

    /**
     * A TaskItem in the graph, when invoked performs actions (create, update or delete) on an
     * external child resource it composes.
     */
    private class ExternalChildActionTaskItem extends IndexableTaskItem {
        /**
         * The composed external child resource.
         */
        private final ExternalChildResourceImpl<FluentModelT, InnerModelT, ParentImplT, ParentT> externalChild;

        /**
         * Creates ExternalChildActionTaskItem.
         *
         * @param externalChild an external child this TaskItem composes.
         */
        ExternalChildActionTaskItem(final ExternalChildResourceImpl<FluentModelT, InnerModelT, ParentImplT, ParentT> externalChild) {
            this.externalChild = externalChild;
        }

        /**
         * Creates ExternalChildActionTaskItem.
         *
         * @param key the task group key for this item
         * @param externalChild an external child this TaskItem composes.
         */
        ExternalChildActionTaskItem(final String key, final ExternalChildResourceImpl<FluentModelT, InnerModelT, ParentImplT, ParentT> externalChild) {
            super(key);
            this.externalChild = externalChild;
        }

        @Override
        public void beforeGroupInvoke() {
            this.externalChild.beforeGroupCreateOrUpdate();
        }

        @Override
        public Observable<Indexable> invokeTaskAsync(TaskGroup.InvocationContext context) {
            switch (this.externalChild.pendingOperation()) {
                case ToBeCreated:
                    return this.externalChild.createResourceAsync()
                            .doOnNext(new Action1<FluentModelT>() {
                                @Override
                                public void call(FluentModelT createdExternalChild) {
                                    externalChild.setPendingOperation(PendingOperation.None);
                                }
                            })
                            .map(new Func1<FluentModelT, Indexable>() {
                                @Override
                                public Indexable call(FluentModelT createdExternalChild) {
                                    return createdExternalChild;
                                }
                            });
                case ToBeUpdated:
                    return this.externalChild.updateResourceAsync()
                            .doOnNext(new Action1<FluentModelT>() {
                                @Override
                                public void call(FluentModelT createdExternalChild) {
                                    externalChild.setPendingOperation(PendingOperation.None);
                                }
                            })
                            .map(new Func1<FluentModelT, Indexable>() {
                                @Override
                                public Indexable call(FluentModelT updatedExternalChild) {
                                    return updatedExternalChild;
                                }
                            });
                case ToBeRemoved:
                    // With 2.0 runtime, deleteResourceAsync() will be returning 'Completable' then use below code instead
                    //
                    //  return this.externalChild.deleteResourceAsync().doOnCompleted(new Action0() {
                    //      @Override
                    //      public void call() {
                    //          externalChild.setPendingOperation(PendingOperation.None);
                    //      }
                    //  }).andThen(voidObservable());
                    //
                    return this.externalChild.deleteResourceAsync()
                            .doOnNext(new Action1<Void>() {
                                @Override
                                public void call(Void aVoid) {
                                    externalChild.setPendingOperation(PendingOperation.None);
                                }
                            })
                            .map(new Func1<Void, Indexable>() {
                                @Override
                                public Indexable call(Void aVoid) {
                                    return voidIndexable();
                                }
                            });
                default:
                    // PendingOperation.None
                    //
                    return Observable.error(new IllegalStateException("No action pending on child resource: " + externalChild.name + ", invokeAsync should not be called "));
            }
        }

        @Override
        public Completable invokeAfterPostRunAsync(boolean isGroupFaulted) {
            return this.externalChild.afterPostRunAsync(isGroupFaulted);
        }
    }
}