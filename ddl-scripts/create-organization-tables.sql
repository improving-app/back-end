
CREATE TABLE IF NOT EXISTS public.organization (
    org_id uuid DEFAULT gen_random_uuid() PRIMARY KEY,
    parent uuid,
    name VARCHAR(255),
    status organizationstatus,
    members uuid[],
    owners uuid[],
    FOREIGN KEY (parent) REFERENCES organization(org_id)
);
CREATE TYPE public.organizationstatus AS ENUM ('ORGANIZATION_STATUS_DRAFT', 'ORGANIZATION_STATUS_ACTIVE', 'ORGANIZATION_STATUS_SUSPENDED', 'ORGANIZATION_STATUS_RELEASED', 'ORGANIZATION_STATUS_TERMINATED');
CREATE TABLE IF NOT EXISTS public.info (
    info_id uuid DEFAULT gen_random_uuid() PRIMARY KEY,
    org_id uuid NOT NULL,
    name VARCHAR(255) NOT NULL,
    short_name VARCHAR(255),
    is_private BOOLEAN,
    url VARCHAR(255),
    logo VARCHAR(255),
    tenant_id uuid,
--    address_id uuid,
--    FOREIGN KEY (address_id) REFERENCES address(address_id),
--    FOREIGN KEY (tenant_id) REFERENCES tenant(tenant_id),
    FOREIGN KEY (org_id) REFERENCES organization(org_id),
    UNIQUE(org_id)
);
CREATE TYPE public.postalcodetype AS ENUM ('caPostalCode', 'usPostalCode');
CREATE TABLE IF NOT EXISTS public.address (
    address_id uuid DEFAULT gen_random_uuid() PRIMARY KEY,
    line1 VARCHAR(255) NOT NULL,
    line2 VARCHAR(255),
    city VARCHAR(255) NOT NULL,
    state_province VARCHAR(255) NOT NULL,
    country VARCHAR(255),
    postal_code VARCHAR(255),
    postal_code_type postalcodetype,
    info_id uuid NOT NULL,
    FOREIGN KEY (info_id) REFERENCES info(info_id),
    UNIQUE(info_id)
);
CREATE TABLE IF NOT EXISTS public.metainfo (
    metainfo_id uuid DEFAULT gen_random_uuid() PRIMARY KEY,
    org_id uuid NOT NULL,
    created_on TIMESTAMP NOT NULL,
    created_by uuid NOT NULL,
    last_updated TIMESTAMP,
    last_updated_by uuid,
    status organizationstatus,
    children uuid[],
    FOREIGN KEY (org_id) REFERENCES organization(org_id),
    UNIQUE(org_id)
);
CREATE TABLE IF NOT EXISTS public.tenant (
    tenant_id uuid DEFAULT gen_random_uuid() PRIMARY KEY,
    org_id uuid NOT NULL,
    FOREIGN KEY (org_id) REFERENCES organization(org_id),
    UNIQUE(org_id)
);
CREATE TABLE IF NOT EXISTS public.member (
    member_id uuid DEFAULT gen_random_uuid() PRIMARY KEY,
    org_id uuid NOT NULL,
--    meta_id uuid,
--    FOREIGN KEY (meta_id) REFERENCES meta(meta_id),
    FOREIGN KEY (org_id) REFERENCES organization(org_id)
);
CREATE TABLE IF NOT EXISTS public.owner (
    owner_id uuid DEFAULT gen_random_uuid() PRIMARY KEY,
    org_id uuid NOT NULL,
    FOREIGN KEY (org_id) REFERENCES organization(org_id)
);
CREATE TABLE IF NOT EXISTS public.contact (
    contact_id uuid DEFAULT gen_random_uuid() PRIMARY KEY,
    primary_contacts uuid[],
    billing_contacts uuid[],
    distribution_contacts uuid[],
    org_id uuid NOT NULL,
    FOREIGN KEY (org_id) REFERENCES organization(org_id),
    UNIQUE(org_id)
);

WITH insert_organization AS (
INSERT INTO organization (org_id,
		info_id,
		parent,
		meta_id,
		name,
		status,
		members,
		owners)
		VALUES('c5412655-cf9e-4f7a-b78e-06336fd49185',
			'c5412255-cf9e-4f7a-b78e-06336fd49185',
			NULL,
			'c5412655-cf9e-4f7a-b78e-06936fd49185',
			'test',
			'ORGANIZATION_STATUS_DRAFT',
			ARRAY ['c5412655-cf9e-4f7a-b78e-06336fd49182']::uuid[],
			ARRAY ['c5412655-cf9e-4f7a-b78e-06336fd49182']::uuid[]) ON CONFLICT (org_id)
		DO
		UPDATE
		SET
			info_id = 'c5412255-cf9e-4f7a-b78e-06336fd49185',
			parent = null,
			meta_id = 'c5412255-cf9e-4f7a-b78e-06336fd49185',
			name = 'test1',
			status = 'ORGANIZATION_STATUS_ACTIVE',
			members = ARRAY ['c5412655-cf9e-4f7a-b78e-06336fd49182']::uuid[],
			owners = ARRAY ['c5412655-cf9e-4f7a-b78e-06336fd49182']::uuid[]
		RETURNING
			org_id
),
insert_info AS (
INSERT INTO info (org_id,
		name,
		short_name,
		is_private,
		url,
		logo,
		tenant_id,
		address_id)
SELECT
	org_id,
	'name',
	'short_name',
	FALSE,
	'url',
	'logo',
	'c5412655-cf9e-4f7a-b78e-06336fd49187',
	'c5412655-cf9e-4f7a-b78e-06336fd49182'
FROM
	insert_organization ON CONFLICT (org_id)
	DO UPDATE
	SET
		name = 'name1',
		short_name = 'short_name1',
		is_private = TRUE,
		url = 'url1',
		logo = 'logo1',
		tenant_id = 'c5412655-cf9e-4f7a-b78e-06336fd49188',
		address_id = 'c5412655-cf9e-4f7a-b78e-06336fd49182'
	RETURNING
		info_id -- this is necessary for CTE, but not used
),
insert_metainfo AS (
INSERT INTO metainfo (org_id, created_on, created_by, last_updated, last_updated_by, status, children)
SELECT
	org_id,
	'2020-06-22 19:10:25-07',
	'c5412655-cf9e-4f7a-b78e-06336fd49189',
	'2020-06-22 19:10:25-07',
	'c5412655-cf9e-4f7a-b78e-06336fd49180',
	'ORGANIZATION_STATUS_ACTIVE',
	 ARRAY ['c5412655-cf9e-4f7a-b78e-06336fd49182']::uuid[]
FROM
	insert_organization ON CONFLICT (org_id)
	DO UPDATE
	SET
	created_on = '2020-06-22 19:10:25-07', created_by = 'c5412655-cf9e-4f7a-b78e-06336fd49185', last_updated = '2020-06-22 19:10:25-09', last_updated_by ='c5412655-cf9e-4f7a-b78e-06336fd49183', status = 'ORGANIZATION_STATUS_ACTIVE', children = ARRAY ['c5412655-cf9e-4f7a-b78e-06336fd49183']::uuid[]
	RETURNING metainfo_id
)
UPDATE organization
SET info_id = subquery.info_id,
    meta_id = subquery.metainfo_id
FROM (SELECT insert_organization.org_id, insert_info.info_id, insert_metainfo.metainfo_id FROM insert_organization, insert_info, insert_metainfo) AS subquery
WHERE organization.org_id=subquery.org_id;