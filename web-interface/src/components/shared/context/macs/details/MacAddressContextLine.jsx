import React, {useEffect, useState} from "react";
import WithPermission from "../../../../misc/WithPermission";
import MacAddressContextForm from "../../../../context/macs/MacAddressContextForm";
import ContextService from "../../../../../services/ContextService";
import {toast} from "react-toastify";
import useSelectedTenant from "../../../../system/tenantselector/useSelectedTenant";
import LoadingSpinner from "../../../../misc/LoadingSpinner";

const contextService = new ContextService();

export default function MacAddressContextLine({address, context, onChange = () => {}}) {

  const [organizationId, tenantId] = useSelectedTenant();

  const [modalExtended, setModalExtended] = useState(false);
  const [modalMode, setModalMode] = useState("");
  const [uuid, setUuid] = useState(null);

  const [errorMessage, setErrorMessage] = useState(null);

  useEffect(() => {
    if (context) {
      contextService.findMacAddressContext(address, organizationId, tenantId, (ctx) => {
        setUuid(ctx.context.uuid)
      })
    }
  }, [context, address, organizationId, tenantId]);

  const onModalClose = () => {
    setModalExtended(false);
  }

  const showModal = (mode) => {
    setModalMode(mode);
    setModalExtended(true);
  }

  const onSubmit = (macAddress, name, description, notes, organizationId, tenantId, onComplete) => {
    if (modalMode === "CREATE") {
      contextService.createMacAddressContext(
        macAddress,
        name,
        description,
        notes,
        organizationId,
        tenantId,
        () => {
          toast.success('Context created.');
          onComplete();
          onChange();
          setModalExtended(false);
        },
        (error) => {
          toast.error('Could not create context.');
          setErrorMessage(error.response.data.message);
          onComplete();
        }
      )
    }

    if (modalMode === "EDIT") {
      contextService.editMacAddressContext(uuid, name, description, notes, organizationId, tenantId, () => {
        toast.success('Context updated.');
        onComplete();
        onChange();
        setModalExtended(false);
      }, () => {
        onComplete();
      });
    }
  }

  const onDelete = () => {
    if (!confirm("Really delete context?")) {
      return;
    }

    contextService.deleteMacAddressContext(uuid, organizationId, tenantId, () => {
      setModalExtended(false);
      onChange();
    })
  }

  const modal = () => {
    if (!modalExtended) {
      return null;
    }

    return (
      <React.Fragment>
        <div className="modal-backdrop fade show"></div>
        <div className="modal fade show" style={{display: "block"}}>
          <div className="modal-dialog modal-lg modal-dialog-centered modal-dialog-scrollable">
            <div className="modal-content">
              <div className="modal-header">
                <h1 className="modal-title fs-5">{modalMode === "CREATE" ? "Create Context" : "Edit Context"}</h1>
                <button type="button" className="btn-close" onClick={onModalClose}></button>
              </div>
              <div className="modal-body">
                <MacAddressContextForm submitText={modalMode === "CREATE" ? "Create Context" : "Update Context"}
                                       macAddressDisabled={true}
                                       onSubmit={onSubmit}
                                       onDelete={onDelete}
                                       macAddress={address}
                                       name={context ? context.name : null}
                                       description={context ? context.description : null}
                                       notes={context ? context.notes : null}
                                       organizationId={organizationId}
                                       tenantId={tenantId}
                                       errorMessage={errorMessage} />
              </div>
            </div>
          </div>
        </div>
      </React.Fragment>
    )
  }

  if (context && !uuid) {
    return <LoadingSpinner />;
  }

  if (!context) {
    return (
        <React.Fragment>
          No Context Configured{' '}
          <WithPermission permission="mac_context_manage">
            (<a href="#" onClick={(e) => { e.preventDefault(); showModal("CREATE"); }}>
              Add Context
            </a>)

            {modal()}
          </WithPermission>
        </React.Fragment>
    )
  }

  return (
      <React.Fragment>
        {context.name ? <span className="context-name">{context.name}</span> : <span className="text-muted">No Name</span> }
        {' '}
        {context.description ? <span>({context.description})</span> : null }
        {' '}
        <WithPermission permission="mac_context_manage">
          (<a href="#" onClick={(e) => { e.preventDefault(); showModal("EDIT"); }}>
            Edit Context
          </a>)

          {modal()}
        </WithPermission>

      </React.Fragment>
  )

}