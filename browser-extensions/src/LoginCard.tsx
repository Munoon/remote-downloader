import React, {EventHandler, useContext, useState} from "react";
import { TextField } from "@/ui/components/TextField";
import { Button } from "@/ui/components/Button";
import { FeatherAlertCircle } from "@subframe/core";
import { ConnectionContext, UserCredentialsContext } from "./context";
import WebSocketClient, { buildOnWebSocketClosedHandler, buildOnWebSocketErrorHandler } from "./api/client";
import browserClient, { UserCredentials } from "./browserClient.tsx";
import { sha256 } from "js-sha256";

export default function LoginCard() {
  const { setCredentials } = useContext(UserCredentialsContext);
  const { setConnection } = useContext(ConnectionContext);
  const [{ connecting, failedToConnectReason }, setNewConnection] = useState<{
    connecting: boolean,
    failedToConnectReason?: string
  }>({ connecting: false })

  const [address, setAddress] = useState('');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');

  const [addressValidation, setAddressValidation] = useState('');
  const [usernameValidation, setUsernameValidation] = useState('');
  const [passwordValidation, setPasswordValidation] = useState('');

  function validateAddress(address: string) {
    if (address.length === 0) {
      setAddressValidation('Please, provide an address.');
      return false;
    }
    
    const basicPattern = /^[a-zA-Z0-9.-]+(:\d+)?$/;
    if (!basicPattern.test(address)) {
      setAddressValidation("Address must contain only letters, digits, '.', '-', and optional ':port'.");
      return false;
    }

    const [host, portStr] = address.split(':');
    if (!host) {
      setAddressValidation('Host is missing.');
      return false;
    }

    if (portStr !== undefined) {
      const port = Number(portStr);
      if (isNaN(port) || port < 1 || port > 65535) {
        setAddressValidation('Port must be a number between 1 and 65535.');
        return false;
      }
    }

    if (addressValidation.length > 0) {
      setAddressValidation('');
    }
    return true;
  }

  function validateUsername(username: string) {
    if (username.length === 0) {
      setUsernameValidation('Please, provide a username.');
      return false;
    }
    if (username.length > 255) {
      setUsernameValidation('Username is too large (max length is 255 symbols).');
      return false;
    }

    // noinspection SpellCheckingInspection
    const allowedChars = 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_.@';
    for (let i = 0; i < username.length; i++) {
      if (!allowedChars.includes(username[i])) {
        setUsernameValidation(`Invalid character '${username[i]}'. Only letters, digits, underscore (_), dot (.), and @ are allowed.`);
        return false;
      }
    }

    if (usernameValidation.length > 0) {
      setUsernameValidation('');
    }
    return true;
  }

  function validatePassword(password: string) {
    if (password.length === 0) {
      setPasswordValidation('Please, provide a password.');
      return false;
    }

    if (passwordValidation.length > 0) {
      setPasswordValidation('');
    }
    return true;
  }

  const onChangeFactory = (
    setter: (str: string) => void,
    validationMessage: string,
    validator: (arg: string) => any
  ) => (e: React.ChangeEvent<HTMLInputElement>) => {
    e.preventDefault();

    const value = e.target.value;
    setter(value);
    if (validationMessage.length > 0) {
      validator(value);
    }
  };

  const onSubmit: EventHandler<any> = (e) => {
    e.preventDefault();

    const addressValid = validateAddress(address);
    const usernameValid = validateUsername(username);
    const passwordValid = validatePassword(password);
    if (!addressValid || !usernameValid || !passwordValid) {
      return;
    }

    const newCredentials: UserCredentials = {
      address,
      username,
      passwordEncrypted: sha256(password + username)
    }

    const client = new WebSocketClient(`ws://${address}/websocket`, newCredentials, {
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
      onError(error) {
        setNewConnection({
          connecting: false,
          failedToConnectReason: error.message
        })
      }
    });

    setNewConnection({ connecting: true });
  };

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
            helpText={addressValidation}
            error={addressValidation.length > 0}
          >
            <TextField.Input
              placeholder="127.0.0.1:8080"
              value={address}
              onChange={onChangeFactory(setAddress, addressValidation, validateAddress)}
              disabled={connecting}
            />
          </TextField>
          <div className="flex h-px w-full flex-none flex-col items-center gap-2 bg-neutral-border" />
          <div className="flex w-full flex-col items-start gap-4">
            <TextField
              className="h-auto w-full flex-none"
              label="Username"
              helpText={usernameValidation}
              error={usernameValidation.length > 0}
            >
              <TextField.Input
                placeholder="Enter username..."
                value={username}
                onChange={onChangeFactory(setUsername, usernameValidation, validateUsername)}
                disabled={connecting}
              />
            </TextField>
            <TextField
              className="h-auto w-full flex-none"
              helpText={addressValidation}
              error={passwordValidation.length > 0}
            >
              <TextField.Input
                type="password"
                placeholder="Enter password..."
                value={password}
                onChange={onChangeFactory(setPassword, passwordValidation, validatePassword)}
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
          disabled={connecting || addressValidation.length > 0 || usernameValidation.length > 0 || passwordValidation.length > 0}
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