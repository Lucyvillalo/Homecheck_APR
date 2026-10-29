"""Integration tests: official local Auth (9099) and Firestore (8080) emulators only.
No credentials or production endpoints. Start demo-homecheck-reset emulators first.
"""
import datetime
import hashlib
import json
import uuid
import urllib.request
import urllib.error

PROJECT = "demo-homecheck-reset"
ROOT = f"http://127.0.0.1:8080/v1/projects/{PROJECT}/databases/(default)/documents"

def http(url, data=None, token=None, method=None):
    headers = {"Content-Type": "application/json"}
    if token: headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(url, json.dumps(data).encode() if data is not None else None, headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=30) as response:
            return response.status, json.load(response)
    except urllib.error.HTTPError as e:
        return e.code, json.load(e)

def value(v):
    if isinstance(v, bool): return {"booleanValue": v}
    if isinstance(v, int): return {"integerValue": str(v)}
    if isinstance(v, list): return {"arrayValue": {"values": [value(x) for x in v]}}
    if isinstance(v, dict): return v
    return {"stringValue": v}

def fields(data): return {k: value(v) for k, v in data.items()}
def write(path, data): return {"update": {"name": ROOT.split("/v1/")[1] + "/" + path, "fields": fields(data)}}
def delete(path): return {"delete": ROOT.split("/v1/")[1] + "/" + path}
def commit(token, *writes): return http(ROOT + ":commit", {"writes": list(writes)}, token)
def put(path, data, token): return http(ROOT + "/" + path, {"fields": fields(data)}, token, "PATCH")
def get(path, token=None): return http(ROOT + "/" + path, token=token)
def expect(status, result, label):
    assert result[0] == status, (label, result)
    print("PASS", label)

def user(role):
    email = "qa-" + uuid.uuid4().hex + "@example.com"
    status, data = http("http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:signUp?key=fake-api-key", {"email":email,"password":"EmulatorOnly!2026","returnSecureToken":True})
    assert status == 200, data
    uid, token = data["localId"], data["idToken"]
    expect(200, put("usuarios/" + uid, {"idUsuario":uid,"rol":role}, token), "create " + role + " profile")
    return uid, token

def run():
    owner, ot = user("Propietario")
    tenant, tt = user("Inquilino")
    outsider, xt = user("Inquilino")
    p = "qa-" + uuid.uuid4().hex
    path = "properties/" + p
    prop = {"ownerUid":owner,"name":"Casa QA","address":"Dirección privada 123","type":"Casa","tenantUid":"","memberUids":[owner],"activeInviteId":""}
    expect(403, put(path, prop, tt), "tenant cannot create an owner property")
    expect(200, put(path, prop, ot), "owner creates property")
    expect(403, get(path, xt), "outsider cannot read private address")
    forged = dict(prop, tenantUid=tenant, memberUids=[owner,tenant])
    expect(403, put(path, forged, tt), "tenant cannot claim property without invitation")
    listing = {"ownerUid":owner,"title":"Casa QA","zone":"Zona pública","description":"Una descripción de alquiler para QA","priceCents":50000,"published":True}
    expect(403, put("rentalListings/" + p, listing, xt), "outsider cannot publish")
    expect(403, put("rentalListings/" + p, dict(listing, address=prop["address"]), ot), "public listing rejects private fields")
    expect(200, put("rentalListings/" + p, listing, ot), "owner publishes sanitized listing")
    expect(200, get("rentalListings/" + p), "public catalog is readable")
    app = "rentalApplications/" + p + "_" + tenant
    expect(404, get(app, tt), "applicant can check their missing application")
    expect(403, get(app, xt), "outsider cannot check another missing application")
    application = {"propertyId":p,"ownerUid":owner,"applicantUid":tenant,"applicantName":"Inquilino QA","status":"pending","inviteCode":""}
    expect(200, put(app, application, tt), "tenant sends application")
    expect(403, get(app, xt), "application is private")
    expect(403, put(app, dict(application,status="approved"), tt), "tenant cannot approve own application")
    code = uuid.uuid4().hex
    invite_id = hashlib.sha256(code.encode()).hexdigest()
    invpath = "propertyInvites/" + invite_id
    expiry = (datetime.datetime.now(datetime.timezone.utc) + datetime.timedelta(days=7)).isoformat().replace("+00:00","Z")
    invite = {"ownerUid":owner,"propertyId":p,"propertyName":"Casa QA","targetUid":tenant,"usedBy":"","status":"pending","expiresAt":{"timestampValue":expiry}}
    prop["activeInviteId"] = invite_id
    application.update(status="approved",inviteCode=code)
    expect(200, commit(ot, write(path,prop),write(invpath,invite),write(app,application)), "owner approves with atomic targeted invitation")
    forged = dict(prop, tenantUid=outsider, memberUids=[owner,outsider])
    expect(403, commit(xt,write(path,forged),write(invpath,dict(invite,status="used",usedBy=outsider))), "targeted invitation rejects another account")
    linked = dict(prop, tenantUid=tenant, memberUids=[owner,tenant])
    used = dict(invite,status="used",usedBy=tenant)
    expect(200, commit(tt,write(path,linked),write(invpath,used),delete("rentalListings/"+p)), "tenant atomically accepts and becomes member")
    expect(200, get(path,tt), "linked tenant reads private property")
    expect(403, put(path,dict(linked,ownerUid=tenant),tt), "tenant cannot replace owner")
    sharedpath = path + "/inspections/" + tenant + "_inspection"
    expect(200, put(sharedpath,{"authorUid":tenant,"payload":"{}"},tt), "tenant shares inspection metadata")
    expect(200, get(sharedpath,ot), "owner reads shared metadata")
    expect(403, get(sharedpath,xt), "outsider cannot read shared inspection")
    expect(403, put(sharedpath,{"authorUid":owner,"payload":"{}"},ot), "owner cannot overwrite tenant authorship")
    expect(403, put(invpath,dict(used,status="pending",usedBy=""),ot), "used invitation cannot be reset")
    expect(200, put(path,dict(prop,activeInviteId=""),ot), "owner revokes tenant access")
    expect(403, get(path,tt), "revoked tenant loses private access")
    expect(403, get(sharedpath,tt), "revocation includes inspection access")
    expect(403, commit(tt,write(path,linked),write(invpath,used),delete("rentalListings/"+p)), "consumed invitation cannot restore revoked access")
    expect(200, http(ROOT + "/rentalListings/" + p,token=ot,method="DELETE"), "owner withdraws listing")
    expect(403, get("rentalListings/" + p), "withdrawn listing no longer public")
    print("ALL PROPERTY RULES CHECKS PASSED (local emulators only)")

if __name__ == "__main__": run()
