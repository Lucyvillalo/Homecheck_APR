"""Adversarial role integration tests. Official local Auth/Firestore only; no production credentials."""
from pathlib import Path
import importlib.util
import datetime
import hashlib
import uuid
spec = importlib.util.spec_from_file_location("property_tests", Path(__file__).with_name("test-property-rules.py"))
t = importlib.util.module_from_spec(spec); spec.loader.exec_module(t)


def run():
    owner, ot = t.user("Propietario")
    other_owner, oot = t.user("Propietario")
    tenant, tt = t.user("Inquilino")
    staff, st = t.user("Encargado")
    other_staff, ost = t.user("Encargado")
    applicant, at = t.user("Inquilino")
    late_tenant, lt = t.user("Inquilino")
    pid = "roles-"+uuid.uuid4().hex; path = "properties/"+pid
    prop = {"ownerUid":owner,"name":"Casa roles QA","address":"Dirección privada 123","type":"Casa","tenantUid":"","memberUids":[owner],"activeInviteId":""}
    t.expect(403,t.put("usuarios/"+staff,{"idUsuario":staff,"rol":"Propietario"},st),"staff cannot promote their own stored role")
    t.expect(403,t.put(path,dict(prop,ownerUid=staff,memberUids=[staff]),st),"staff cannot register a property even in their own name")
    t.expect(200,t.put(path,prop,ot),"owner registers property")
    t.expect(403,t.get(path,st),"staff cannot read a property before assignment")
    legacy = "properties/legacy-"+uuid.uuid4().hex
    t.expect(200,t.put(legacy,dict(prop,ownerUid=staff,memberUids=[staff]),"owner"),"seed legacy staff-created property using emulator admin")
    t.expect(200,t.get(legacy,st),"legacy staff record remains readable")
    t.expect(403,t.put(legacy,dict(prop,ownerUid=staff,memberUids=[staff],name="Changed legacy"),st),"legacy staff ownership does not grant management")
    listing = {"ownerUid":owner,"title":"Casa roles QA","zone":"Zona pública","description":"Descripción pública para prueba de roles","priceCents":40000,"published":True}
    t.expect(403,t.put("rentalListings/"+legacy.split('/')[-1],dict(listing,ownerUid=staff),st),"legacy staff owner cannot publish")
    t.expect(200,t.put("rentalListings/"+pid,listing,ot),"owner publishes property")
    application = {"propertyId":pid,"ownerUid":owner,"applicantUid":tenant,"applicantName":"Inquilino QA","status":"pending","inviteCode":""}
    app = "rentalApplications/"+pid+"_"+tenant
    t.expect(200,t.put(app,application,tt),"tenant requests rental")
    pending_app = "rentalApplications/"+pid+"_"+applicant
    pending = dict(application,applicantUid=applicant)
    t.expect(200,t.put(pending_app,pending,at),"another tenant can submit pending request while available")
    t.expect(403,t.get(pending_app,st),"staff cannot read rental requests")
    t.expect(403,t.put(pending_app,dict(pending,status="approved"),st),"staff cannot approve rental requests")
    t.expect(403,t.put("rentalApplications/"+pid+"_"+staff,dict(application,applicantUid=staff),st),"staff cannot submit a tenant request")
    lease_code=uuid.uuid4().hex; lease_id=hashlib.sha256(lease_code.encode()).hexdigest(); lease_path="propertyInvites/"+lease_id
    expiry=(datetime.datetime.now(datetime.timezone.utc)+datetime.timedelta(days=7)).isoformat().replace("+00:00","Z")
    lease={"ownerUid":owner,"propertyId":pid,"propertyName":prop["name"],"targetUid":tenant,"usedBy":"","status":"pending","expiresAt":{"timestampValue":expiry}}
    prop["activeInviteId"]=lease_id
    t.expect(200,t.commit(ot,t.write(path,prop),t.write(lease_path,lease),t.write(app,dict(application,status="approved",inviteCode=lease_code))),"owner approves and invites tenant")
    t.expect(403,t.get(lease_path,st),"tenant invitation is not a staff invitation")
    staff_as_tenant=dict(prop,tenantUid=staff,memberUids=[owner,staff])
    t.expect(403,t.commit(st,t.write(path,staff_as_tenant),t.write(lease_path,dict(lease,status="used",usedBy=staff)),t.delete("rentalListings/"+pid)),"staff cannot claim a tenant relationship")
    linked=dict(prop,tenantUid=tenant,memberUids=[owner,tenant]); lease_used=dict(lease,status="used",usedBy=tenant)
    t.expect(403,t.commit(tt,t.write(path,linked),t.write(lease_path,lease_used)),"rental acceptance must withdraw the public listing atomically")
    t.expect(200,t.commit(tt,t.write(path,linked),t.write(lease_path,lease_used),t.delete("rentalListings/"+pid)),"tenant accepts and withdraws availability atomically")
    t.expect(403,t.get("rentalListings/"+pid),"rented property is no longer advertised")
    t.expect(403,t.put("rentalListings/"+pid,listing,ot),"owner cannot advertise an occupied property")
    t.expect(403,t.put(pending_app,dict(pending,status="approved",inviteCode=uuid.uuid4().hex),ot),"owner cannot approve a second tenant for an occupied property")
    t.expect(200,t.put("rentalListings/"+pid,listing,"owner"),"seed an occupied legacy advertisement with emulator admin")
    t.expect(403,t.put("rentalApplications/"+pid+"_"+late_tenant,dict(application,applicantUid=late_tenant),lt),"occupied legacy advertisement rejects a correctly formed tenant request")
    t.expect(200,t.http(t.ROOT+"/rentalListings/"+pid,token="owner",method="DELETE"),"remove the legacy advertisement fixture")
    # A separate vacant listing proves role checks independently of occupancy/ownership.
    other_id="roles-"+uuid.uuid4().hex; other_path="properties/"+other_id
    other_prop=dict(prop,ownerUid=other_owner,memberUids=[other_owner],activeInviteId="")
    t.expect(200,t.put(other_path,other_prop,oot),"another owner registers property")
    t.expect(200,t.put("rentalListings/"+other_id,dict(listing,ownerUid=other_owner),oot),"another owner publishes vacant property")
    t.expect(403,t.put("rentalApplications/"+other_id+"_"+owner,dict(application,propertyId=other_id,ownerUid=other_owner,applicantUid=owner),ot),"owner role cannot act as a tenant")
    t.expect(403,t.put("rentalApplications/"+other_id+"_"+staff,dict(application,propertyId=other_id,ownerUid=other_owner,applicantUid=staff),st),"staff role cannot act as a tenant on a vacant listing")
    # Owner creates a separate operational invitation without altering rental membership.
    code=uuid.uuid4().hex; iid=hashlib.sha256(code.encode()).hexdigest(); ipath="staffInvites/"+iid
    invite={"ownerUid":owner,"propertyId":pid,"propertyName":prop["name"],"status":"pending","usedBy":"","expiresAt":{"timestampValue":expiry}}
    linked["activeStaffInviteId"]=iid
    t.expect(200,t.commit(ot,t.write(path,linked),t.write(ipath,invite)),"owner creates a separate operational invitation")
    aid="staffAssignments/"+pid+"_"+staff
    assignment={"propertyId":pid,"ownerUid":owner,"staffUid":staff,"staffName":"Encargado QA","inviteId":iid}
    t.expect(403,t.put(aid,assignment,st),"staff cannot self-assign without consuming invitation atomically")
    t.expect(403,t.put(aid,assignment,ot),"owner cannot fabricate staff acceptance")
    tenant_assignment=dict(assignment,staffUid=tenant)
    t.expect(403,t.commit(tt,t.write("staffAssignments/"+pid+"_"+tenant,tenant_assignment),t.write(ipath,dict(invite,status="used",usedBy=tenant))),"tenant cannot consume an operational invitation")
    t.expect(200,t.commit(st,t.write(aid,assignment),t.write(ipath,dict(invite,status="used",usedBy=staff))),"staff accepts explicit operational delegation")
    t.expect(200,t.get(aid,st),"staff sees own assignment")
    t.expect(200,t.get(aid,ot),"owner sees their delegated assignment")
    t.expect(403,t.get(aid,tt),"tenant cannot access staff administration")
    t.expect(403,t.get(aid,ost),"another staff account cannot inspect this assignment")
    t.expect(403,t.get(aid,oot),"another owner cannot inspect a private delegation")
    t.expect(200,t.get(path,st),"assigned staff reads the property")
    t.expect(403,t.get(other_path,st),"assignment does not grant access to another property")
    actual=t.get(path,ot)[1]["fields"]
    assert actual["ownerUid"]["stringValue"]==owner and actual["tenantUid"]["stringValue"]==tenant
    assert [v["stringValue"] for v in actual["memberUids"]["arrayValue"]["values"]]==[owner,tenant]
    print("PASS operational delegation preserves owner and tenant membership")
    shared=path+"/inspections/"+staff+"_inspection"
    t.expect(200,t.put(shared,{"authorUid":staff,"payload":"{}"},st),"assigned staff records inspection data")
    t.expect(200,t.get(shared,tt),"tenant reads delegated inspection data")
    t.expect(200,t.get(shared,ot),"owner reads delegated inspection data")
    t.expect(403,t.put(path,dict(linked,name="Staff edit"),st),"staff cannot modify property details")
    t.expect(403,t.put(path,dict(linked,tenantUid="",memberUids=[owner]),st),"staff cannot remove the tenant")
    t.expect(403,t.put(path,dict(linked,ownerUid=staff),st),"staff cannot take ownership")
    t.expect(403,t.put("rentalListings/"+other_id,dict(listing,ownerUid=staff),st),"staff cannot publish advertisements")
    t.expect(403,t.http(t.ROOT+"/rentalListings/"+other_id,token=st,method="DELETE"),"staff cannot withdraw advertisements")
    t.expect(403,t.put(aid,dict(assignment,staffUid=other_staff),st),"staff cannot transfer delegation")
    t.expect(403,t.http(t.ROOT+"/"+aid,token=tt,method="DELETE"),"tenant cannot revoke staff")
    t.expect(403,t.http(t.ROOT+"/"+aid,token=oot,method="DELETE"),"another owner cannot revoke this delegation")
    linked["activeStaffInviteId"]=""
    t.expect(200,t.commit(ot,t.delete(aid),t.write(path,linked)),"owner revokes only the operational assignment")
    t.expect(403,t.get(path,st),"revoked staff loses property access")
    t.expect(403,t.get(shared,st),"revoked staff loses shared inspection access")
    t.expect(403,t.put(shared,{"authorUid":staff,"payload":"{}"},st),"revoked staff cannot continue shared work")
    t.expect(200,t.get(path,tt),"staff revocation does not revoke tenant access")
    t.expect(200,t.get(shared,tt),"tenant retains existing inspection data")
    t.expect(403,t.commit(st,t.write(aid,assignment),t.write(ipath,dict(invite,status="used",usedBy=staff))),"consumed invitation cannot reinstate a revoked assignment")
    print("ALL DISTINCT ROLE CHECKS PASSED (local emulators only)")

if __name__=="__main__":
    t.run()
    run()
