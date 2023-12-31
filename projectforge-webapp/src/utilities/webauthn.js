/* eslint-disable */
const decodeBase64url = (baseurl64String) => {
    const padding = "==".slice(0, (4 - baseurl64String.length % 4) % 4);
    const base64String = baseurl64String.replace(/-/g, "+").replace(/_/g, "/") + padding;
    const str = atob(base64String);
    const buffer = new ArrayBuffer(str.length);
    const byteView = new Uint8Array(buffer);
    for (let i = 0; i < str.length; i++) {
        byteView[i] = str.charCodeAt(i);
    }
    return buffer;
}

const bufferToBase64url = (buffer) => {
    const byteView = new Uint8Array(buffer);
    let str = "";
    for (const charCode of byteView) {
        str += String.fromCharCode(charCode);
    }
    const base64String = btoa(str);
    return base64String.replace(/\+/g, "-").replace(/\//g, "_").replace(/=/g, "");
}

const convertCredentials = (dest, src) => {
    if (src) {
        src.map((credential) => {
            dest.push({
                type: credential.type,
                rawId: decodeBase64url(credential.rawId),
                id: decodeBase64url(credential.id),
            })
        })
    }
}

export const convertPublicKeyCredentialRequestOptions = (publicKeyCredentialCreationOptions) => {
    const result = JSON.parse(JSON.stringify(publicKeyCredentialCreationOptions));
    result.challenge = decodeBase64url(publicKeyCredentialCreationOptions.challenge);
    result.user.id = decodeBase64url(publicKeyCredentialCreationOptions.user.id);
    result.allowCredentials = [];
    convertCredentials(result.allowCredentials, publicKeyCredentialCreationOptions.allowCredentials);
    result.excludeCredentials = [];
    convertCredentials(result.excludeCredentials, publicKeyCredentialCreationOptions.excludeCredentials);
    return result;
}

export const convertRegisterCredential = (credential, publicKeyCredentialCreationOptions) => {
    const { response } = credential;
    return {
        requestId: publicKeyCredentialCreationOptions.requestId,
        credential: {
            type: credential.type,
            id: credential.id,
            rawId: bufferToBase64url(credential.rawId),
            response: {
                clientDataJSON: bufferToBase64url(response.clientDataJSON),
                attestationObject: bufferToBase64url(response.attestationObject),
                transports: response.transports,
            },
        },
        clientExtensionResults: {},
        challenge: publicKeyCredentialCreationOptions.challenge,
        sessionToken: publicKeyCredentialCreationOptions.sessionToken,
    }
}

export const convertAuthenticateCredential = (credential, publicKeyCredentialCreationOptions) => {
    const { response } = credential;
    return {
        requestId: publicKeyCredentialCreationOptions.requestId,
        credential: {
            type: credential.type,
            id: credential.id,
            rawId: bufferToBase64url(credential.rawId),
            response: {
                authenticatorData: bufferToBase64url(response.authenticatorData),
                clientDataJSON: bufferToBase64url(response.clientDataJSON),
                signature: bufferToBase64url(response.signature),
                userHandle: response.userHandle,
            },
        },
        clientExtensionResults: {},
        challenge: publicKeyCredentialCreationOptions.challenge,
        sessionToken: publicKeyCredentialCreationOptions.sessionToken,
    }
}
