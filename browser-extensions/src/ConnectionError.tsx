import { MouseEventHandler, useContext } from "react";
import browserClient from "./browserClient.tsx";
import { ConnectionContext, UserCredentialsContext } from "./context";
import { ErrorMessage } from "./ui/components/ErrorMessage";
import { FeatherAlertCircle } from "@subframe/core";

export default function ConnectionError() {
  const { failedToConnectReason, setConnection } = useContext(ConnectionContext);
  const { setCredentials } = useContext(UserCredentialsContext);

  if (!failedToConnectReason) {
    return null;
  }

  const logout: MouseEventHandler<HTMLSpanElement> = (e) => {
    e.preventDefault();
    setCredentials(undefined);
    browserClient.setCredentials(undefined);
    setConnection({ connected: false, connecting: false, client: undefined, setConnection })
  };

  const text = (
    <div>
      {failedToConnectReason + ' '}
      <span
        className="underline cursor-pointer"
        onClick={logout}>
        Click here to log out.
      </span>
    </div>
  );

  return (
    <ErrorMessage
      icon={<FeatherAlertCircle />}
      text={text}
      />
  );
}