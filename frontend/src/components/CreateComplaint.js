import React, { useEffect, useMemo, useState } from 'react';
import '../styles/CreateComplaint.css';

function CreateComplaint({ onCreate }) {
  const DRAFT_KEY = 'scs.complaintDraft.v1';

  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [category, setCategory] = useState('General');
  const [priority, setPriority] = useState('MEDIUM');
  const [location, setLocation] = useState('');
  const [preferredContact, setPreferredContact] = useState('In App');
  const [incidentDate, setIncidentDate] = useState('');
  const [isAnonymous, setIsAnonymous] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [message, setMessage] = useState('');

  useEffect(() => {
    try {
      const raw = window.localStorage.getItem(DRAFT_KEY);
      if (!raw) {
        return;
      }
      const draft = JSON.parse(raw);
      setTitle(draft.title || '');
      setDescription(draft.description || '');
      setCategory(draft.category || 'General');
      setPriority(draft.priority || 'MEDIUM');
      setLocation(draft.location || '');
      setPreferredContact(draft.preferredContact || 'In App');
      setIncidentDate(draft.incidentDate || '');
      setIsAnonymous(Boolean(draft.isAnonymous));
    } catch (_err) {
      // Ignore invalid draft and continue with empty form.
    }
  }, []);

  useEffect(() => {
    const draft = {
      title,
      description,
      category,
      priority,
      location,
      preferredContact,
      incidentDate,
      isAnonymous
    };
    window.localStorage.setItem(DRAFT_KEY, JSON.stringify(draft));
  }, [title, description, category, priority, location, preferredContact, incidentDate, isAnonymous]);

  const characterInfo = useMemo(() => {
    const len = description.trim().length;
    const min = 20;
    return {
      len,
      meetsMinimum: len >= min,
      helper: len >= min ? `${len} chars` : `${min - len} more chars recommended`
    };
  }, [description]);

  const clearDraft = () => {
    setTitle('');
    setDescription('');
    setCategory('General');
    setPriority('MEDIUM');
    setLocation('');
    setPreferredContact('In App');
    setIncidentDate('');
    setIsAnonymous(false);
    setMessage('Draft cleared.');
    window.localStorage.removeItem(DRAFT_KEY);
  };

  const onSubmit = async (event) => {
    event.preventDefault();
    setMessage('');

    if (!title.trim() || !description.trim()) {
      setMessage('Title and description are required.');
      return;
    }

    if (description.trim().length < 10) {
      setMessage('Please add a little more detail in description.');
      return;
    }

    try {
      setSubmitting(true);
      await onCreate({
        title,
        description,
        category,
        priority,
        location,
        preferredContact,
        incidentDate,
        userVisibility: isAnonymous ? 'ANONYMOUS' : 'VISIBLE'
      });
      setTitle('');
      setDescription('');
      setCategory('General');
      setPriority('MEDIUM');
      setLocation('');
      setPreferredContact('In App');
      setIncidentDate('');
      setIsAnonymous(false);
      window.localStorage.removeItem(DRAFT_KEY);
      setMessage('Complaint submitted successfully.');
    } catch (err) {
      setMessage(err.message || 'Failed to submit complaint.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form className="create-complaint-form" onSubmit={onSubmit}>
      <div className="form-grid two-col">
        <div>
          <label htmlFor="title">Complaint Title</label>
          <input id="title" value={title} onChange={(e) => setTitle(e.target.value)} placeholder="Brief issue summary" maxLength={120} />
        </div>
        <div>
          <label htmlFor="category">Category</label>
          <select id="category" value={category} onChange={(e) => setCategory(e.target.value)}>
            <option>General</option>
            <option>Infrastructure</option>
            <option>Water</option>
            <option>Electricity</option>
            <option>Road & Traffic</option>
            <option>Sanitation</option>
            <option>Safety</option>
            <option>System</option>
          </select>
        </div>
      </div>

      <label htmlFor="description">Description</label>
      <textarea id="description" rows={5} value={description} onChange={(e) => setDescription(e.target.value)} placeholder="Describe the issue in detail" />
      <div className={`description-helper ${characterInfo.meetsMinimum ? 'ok' : 'warn'}`}>{characterInfo.helper}</div>

      <div className="form-grid three-col">
        <div>
          <label htmlFor="priority">Priority</label>
          <select id="priority" value={priority} onChange={(e) => setPriority(e.target.value)}>
            <option value="LOW">LOW</option>
            <option value="MEDIUM">MEDIUM</option>
            <option value="HIGH">HIGH</option>
            <option value="CRITICAL">CRITICAL</option>
          </select>
        </div>
        <div>
          <label htmlFor="location">Location</label>
          <input id="location" value={location} onChange={(e) => setLocation(e.target.value)} placeholder="Area / landmark" />
        </div>
        <div>
          <label htmlFor="incidentDate">Incident Date</label>
          <input id="incidentDate" type="date" value={incidentDate} onChange={(e) => setIncidentDate(e.target.value)} />
        </div>
      </div>

      <div className="form-grid two-col">
        <div>
          <label htmlFor="preferredContact">Preferred Contact</label>
          <select id="preferredContact" value={preferredContact} onChange={(e) => setPreferredContact(e.target.value)}>
            <option>In App</option>
            <option>Email</option>
            <option>Phone</option>
          </select>
        </div>
        <div className="anonymous-toggle-wrap">
          <label className="anonymous-toggle" htmlFor="anonymousFlag">
            <input id="anonymousFlag" type="checkbox" checked={isAnonymous} onChange={(e) => setIsAnonymous(e.target.checked)} />
            Submit as anonymous (hide name from listing)
          </label>
        </div>
      </div>

      <div className="form-actions-row">
        <button type="submit" disabled={submitting}>{submitting ? 'Submitting...' : 'Submit Complaint'}</button>
        <button className="secondary-btn" type="button" onClick={clearDraft}>Clear Draft</button>
      </div>
      {message ? <p className="form-message">{message}</p> : null}
    </form>
  );
}

export default CreateComplaint;
