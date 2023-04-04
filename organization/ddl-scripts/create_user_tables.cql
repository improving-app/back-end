USE organizationservice;
CREATE TABLE IF NOT EXISTS organization_and_owner (
  org_id text,
  owner_id text,
  organization text,
  PRIMARY KEY (org_id, owner_id));

CREATE TABLE IF NOT EXISTS organization_and_member (
  org_id text,
  member_id text,
  organization text,
  PRIMARY KEY (org_id, member_id));

CREATE TABLE IF NOT EXISTS organization_to_owner (
  org_id text,
  owner_id text,
  organization text,
  PRIMARY KEY (org_id));

CREATE TABLE IF NOT EXISTS organization_to_member (
  org_id text,
  member_id text,
  organization text,
  PRIMARY KEY (org_id));

CREATE TABLE IF NOT EXISTS owner_to_organization (
  org_id text,
  owner_id text,
  organization text,
  PRIMARY KEY (owner_id));

CREATE TABLE IF NOT EXISTS member_to_organization (
  org_id text,
  member_id text,
  organization text,
  PRIMARY KEY (member_id));

CREATE TABLE IF NOT EXISTS organization_to_root (
    org_id text,
    root_id text,
    PRIMARY KEY (org_id));
)

CREATE TABLE IF NOT EXISTS children_to_organization (
    child_id text,
    parent_id text
    PRIMARY KEY (child_id));