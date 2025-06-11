import React, { useContext, useState } from "react";
import { TextField } from "@/ui/components/TextField";
import { Button } from "@/ui/components/Button";
import { FeatherAlertCircle } from "@subframe/core";
import { ConnectionContext, UserCredentialsContext } from "./context";
import WebSocketClient from "./api/client";
import browserClient, { UserCredentials } from "./browser_client";
import { buildOnWebSocketClosedHandler, buildOnWebSocketErrorHandler } from "./App";

export default function LoginCard() {
  const { credentials, setCredentials } = useContext(UserCredentialsContext);
  const { setConnection } = useContext(ConnectionContext);
  const [{ connecting, failedToConnectReason }, setNewConnection] = useState<{
    connecting: boolean,
    failedToConnectReason?: string
  }>({ connecting: false })

  const [address, setAddress] = useState('');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');

  const onChangeFactory = (setter: (str: string) => void) => (e: React.ChangeEvent<HTMLInputElement>) => {
    e.preventDefault();
    setter(e.target.value);
  };

  const onSubmit = (e: { preventDefault: () => void }) => {
    e.preventDefault();

    const newCredentials: UserCredentials = {
      host: address.split(':')[0],
      port: +address.split(':')[1],
      username,
      passwordEncrypted: password
    }

    const client = new WebSocketClient(`ws://${address}/websocket`, {
      onOpen() {
        client.handlers = {
          onOpen() {},
          onClose: buildOnWebSocketClosedHandler(setConnection),
          onError: buildOnWebSocketErrorHandler(setConnection)
        }

        setCredentials(newCredentials);
        setConnection({ connected: true, connecting: false, client, setConnection })
        browserClient.setCredentials(newCredentials);
      },
      onClose() {
        if (connecting) {
          setNewConnection({
            connecting: false,
            failedToConnectReason: 'Connection closed.'
          })
        }
      },
      onError() {
        setNewConnection({
          connecting: false,
          failedToConnectReason: 'Connection failed.'
        })
      }
    });

    setNewConnection({ connecting: true });
  };

  if (credentials) {
    return null;
  }

  return (
    <form onSubmit={onSubmit} className="flex w-full flex-col items-center justify-center gap-8 rounded-md border border-solid border-neutral-border bg-white px-4 py-4 shadow-sm">
      <div className="flex w-full flex-col items-start gap-6">
        <span className="text-heading-2 font-heading-2 text-default-font">
          Server Login
        </span>
        <div className="flex w-full flex-col items-start gap-4">
          <TextField
            className="h-auto w-full flex-none"
            label="Server Address"
            helpText=""
          >
            <TextField.Input
              placeholder="127.0.0.1:8080"
              value={address}
              onChange={onChangeFactory(setAddress)}
              disabled={connecting}
            />
          </TextField>
          <div className="flex h-px w-full flex-none flex-col items-center gap-2 bg-neutral-border" />
          <div className="flex w-full flex-col items-start gap-4">
            <TextField
              className="h-auto w-full flex-none"
              label="Username"
              helpText=""
            >
              <TextField.Input
                placeholder="Enter username..."
                value={username}
                onChange={onChangeFactory(setUsername)}
                disabled={connecting}
              />
            </TextField>
            <TextField
              className="h-auto w-full flex-none"
              label="Password"
              helpText=""
            >
              <TextField.Input
                type="password"
                placeholder="Enter password..."
                value={password}
                onChange={onChangeFactory(setPassword)}
                disabled={connecting}
              />
            </TextField>
          </div>
        </div>
      </div>
      <div className="flex w-full flex-col items-center gap-4">
        <Button
          className="h-10 w-full flex-none"
          size="large"
          onClick={onSubmit}
          disabled={connecting}
          type="submit"
        >
          Log in
        </Button>
        {failedToConnectReason && (
          <div className="flex items-center gap-2">
            <FeatherAlertCircle className="text-body font-body text-error-600" />
            <span className="text-body font-body text-error-600">
              {failedToConnectReason}
            </span>
          </div>
        )}
      </div>
    </form>
  );
}